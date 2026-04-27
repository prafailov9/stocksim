package com.ntros.simulation.stage.impl;

import static com.ntros.MarketUtils.MIN_ALLOWED_CENTS;
import static com.ntros.simulation.model.Side.BUY;
import static com.ntros.simulation.model.Side.SELL;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.control.CancellationToken;
import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Holding;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import com.ntros.simulation.stage.AbstractSimulationStage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderPipeline extends AbstractSimulationStage {

  private static final Logger log = LoggerFactory.getLogger(OrderPipeline.class);
  private final Random RNG = new Random();
  private static final Order POISON = new Order(null, null);

  private final List<Product> products;
  private final List<Trader> traders;
  private final List<ReentrantLock> clientLocks;
  private final List<Object> pricingLocks;
  private final LinkedBoundedQueue<Order> generatedOrders;
  private final LinkedBoundedQueue<Order> placements;
  private final AtomicLong processedCount;
  private final Map<Integer, PriceFlow> priceFlows;

  public OrderPipeline(SimulationContext context) {
    super(context);
    products = context.availableProducts();
    traders = context.traders();
    clientLocks = context.clientLocks();
    pricingLocks = context.pricingLocks();
    generatedOrders = context.generatedOrders();
    placements = context.placements();
    processedCount = context.processedCount();
    priceFlows = context.priceFlows();
  }

  public void poisonGenerators() throws InterruptedException {
    generatedOrders.put(POISON);
  }

  public void poisonPlacers() throws InterruptedException {
    placements.put(POISON);
  }

  // generates an order each pass
  public Runnable generate(CancellationToken cancellationToken) {
    return () -> {
      while (!cancellationToken.isCancelled()) {
        // get market products as list from set

        // init order and add to generatedOrders store
        var trader = traders.get(RNG.nextInt(traders.size()));
        var side = RNG.nextFloat() < 0.50f ? BUY : SELL;
        Set<Product> validatedProducts = new HashSet<>();
        long qty;
        // single order each pass for simplicity, maybe expand later
        if (side == BUY) {
          var product = products.get(RNG.nextInt(products.size()));
          validatedProducts.add(product);
          // determine qty from trader's current spending balance
          long price = product.getPrice();
          long affordableShares = trader.getAccount().getAvailableBalance() / price;
          long sharesToBuy =
              affordableShares > 0
                  ? RNG.nextLong(1, Math.min(affordableShares, 100) + 1)
                  : 1; // cap qty at 100
          qty = sharesToBuy;
        } else { // SELLS
          Map<Product, Holding> ownedHoldings;
          List<Product> ownedProducts;
          ReentrantLock clientLock = clientLocks.get(trader.getId() - 1);
          clientLock.lock();
          try {
            ownedHoldings = trader.getAccount().getPortfolio().getHoldings();
            ownedProducts = new ArrayList<>(ownedHoldings.keySet());
          } finally {
            clientLock.unlock();
          }
          // if trader owns nothing - skip
          if (ownedHoldings.isEmpty()) {
            continue;
          } else {
            // add only one single-product, sell order per cycle, with varying quantities to sell,
            // avg less than 1/3 of total holding

            var randomProduct = ownedProducts.get(RNG.nextInt(ownedProducts.size()));
            var holding = trader.getAccount().getPortfolio().getHoldings().get(randomProduct);
            long productQuantity = holding != null ? holding.getQuantity() : 0;
            if (productQuantity == 0) continue;

            var sellQtyCap = Math.max(1L, productQuantity / 3L);
            var qtyToSell = RNG.nextLong(1, sellQtyCap + 1);

            // select random holding
            validatedProducts.add(randomProduct);
            qty = qtyToSell;
          }
        }
        Order order = new Order(trader, side);
        order.addAllProducts(new ArrayList<>(validatedProducts));
        order.setQuantity(qty);
        try {
          generatedOrders.put(order);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    };
  }

  public Runnable placing() {
    return () -> {
      while (true) {
        Order order;
        try {
          order = generatedOrders.take();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
        if (order == null) {
          continue;
        }
        // poison pill check, any placer receiving a null client will exit before placing a new
        // order
        if (order.getTrader() == null) {
          break;
        }
        // get market products as list from set
        List<Product> orderedProducts = order.getProducts();
        var trader = order.getTrader();
        // discard order if no quantity
        if (order.getQuantity() == 0) {
          log.info("no qty for order: {}", order);
          continue;
        }
        // get total
        long totalPrice = 0L;
        long snapshotPrice = 0L; // price per share at placement time
        for (var product : orderedProducts) {
          synchronized (pricingLocks.get(product.getId() - 1)) {
            // TODO: add fee
            snapshotPrice = product.getPrice();
            totalPrice += snapshotPrice * order.getQuantity();
          }
        }

        var side = order.getSide();
        int clientIdx = order.getTrader().getId() - 1;
        var validatedOrder = new Order(trader, side);
        ReentrantLock clientLock = clientLocks.get(clientIdx);
        clientLock.lock();
        try {
          if (side.equals(BUY)) {
            long availableBalance = trader.getAccount().getAvailableBalance();
            // try full buy
            if (availableBalance > totalPrice
                && availableBalance - totalPrice >= MIN_ALLOWED_CENTS) {
              trader.getAccount().decreaseAvailableBalance(totalPrice);
              trader.getAccount().increaseReservedBalance(totalPrice);
              validatedOrder.addAllProducts(orderedProducts);
              validatedOrder.setQuantity(order.getQuantity());
              validatedOrder.setOrderPrice(snapshotPrice); // snapshot at placement time

            } else {
              // try partial buy
              long partialPrice = 0L;
              // TODO: partial qty logic, should be based off of affordable shares
              long affordableShares = 0L;
              for (var ordered : orderedProducts) {
                long pricePerShare = ordered.getPrice();
                long maxAffordableShares = availableBalance / pricePerShare;
                if (maxAffordableShares == 0) {
                  continue;
                }
                long sharesToBuy = Math.min(order.getQuantity(), maxAffordableShares);
                long cost = sharesToBuy * pricePerShare;

                if (availableBalance - cost >= MIN_ALLOWED_CENTS) {
                  partialPrice += cost;
                  affordableShares = sharesToBuy;
                  validatedOrder.addProduct(ordered);
                  validatedOrder.setOrderPrice(pricePerShare);
                }
              }
              // partial buy failed - skip
              if (partialPrice == 0) {
                //              log.info(
                //                  "!!!BROKIE ALERT!!! Client {} is too broke. TotalBuyPrice: {},
                // minBuyingPowerAllowed: {}, clientAvailableBalance: {}",
                //                  client.getId(),
                //                  Money.bucks(totalPrice),
                //                  Money.bucks(MIN_ALLOWED_CENTS),
                //                  Money.bucks(availableBalance));
                continue;
              }
              trader.getAccount().decreaseAvailableBalance(partialPrice);
              trader.getAccount().increaseReservedBalance(partialPrice);
              validatedOrder.setQuantity(affordableShares);
            }
          } else { // SELL branch
            // check ordered against owned, only allow once who match
            Map<Product, Holding> holdings = trader.getAccount().getPortfolio().getHoldings();
            for (var ordered : orderedProducts) {
              if (holdings.containsKey(ordered)) {
                var qtyToSell = order.getQuantity();
                var currentQty = trader.getAccount().getPortfolio().quantityOf(ordered);

                if (currentQty == 0) {
                  continue; // nothing to sell - skip
                }
                // current holding quantity has been decreased before placement, default to 1
                if (currentQty < qtyToSell) {
                  qtyToSell = 1;
                }

                // snapshot current price at placement time
                long sellPrice;
                synchronized (pricingLocks.get(ordered.getId() - 1)) {
                  sellPrice = ordered.getPrice();
                }

                // reserve
                trader.getAccount().getPortfolio().decreaseHoldingQuantity(ordered, qtyToSell);
                validatedOrder.setQuantity(qtyToSell);
                validatedOrder.setOrderPrice(sellPrice); // snapshot
                validatedOrder.addProduct(ordered);
              }
            }
            // skip placement if no ordered products and owned
            if (validatedOrder.getProducts().isEmpty()) {
              continue;
            }
          }
        } finally {
          clientLock.unlock();
        }
        // place the order
        try {
          placements.put(validatedOrder);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    };
  }

  public Runnable processing() {
    return () -> {
      // poison pill handles control
      while (true) {
        Order order;
        try {
          order = placements.take();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
        // poison pill check, always check first
        if (order.getTrader() == null) {
          break;
        }

        if (order.getProducts().isEmpty()) {
          continue;
        }
        // structure ensures indices are always clientId - 1
        int clientIdx = order.getTrader().getId() - 1;
        // modify account balance
        ReentrantLock clientLock = clientLocks.get(clientIdx);
        clientLock.lock();
        try {
          Account account = order.getTrader().getAccount();
          if (order.side().equals(BUY)) {
            account.decreaseReservedBalance(order.getOrderPrice());
            // add new holding if not owned or increase an existing one's qty
            for (var p : order.getProducts()) {
              if (account.getPortfolio().owns(p)) {
                account.getPortfolio().increaseHoldingQuantity(p, order.getQuantity());
              } else {
                account.getPortfolio().addHolding(p, order.getQuantity());
              }
            }
          } else { // sell
            // portfolio already updated at placement
            account.increaseAvailableBalance(order.getOrderPrice());
          }
        } finally {
          clientLock.unlock();
        }
        processedCount.incrementAndGet();

        // adjust price flow for product
        for (var p : order.getProducts()) {
          var pricingLock = pricingLocks.get(p.getId() - 1);
          synchronized (pricingLock) {
            var priceFlow = priceFlows.get(p.getId());
            if (priceFlow == null) {
              throw new RuntimeException(
                  String.format("No priceFlow exists for productId: %s", p.getId()));
            }
            if (order.getSide().equals(BUY)) {
              priceFlow.increaseBuys();
            } else {
              priceFlow.increaseSells();
            }
          }
        }
      }
    };
  }
}
