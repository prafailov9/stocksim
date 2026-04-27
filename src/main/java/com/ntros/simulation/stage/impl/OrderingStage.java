package com.ntros.simulation.stage.impl;

import static com.ntros.MarketUtils.MIN_ALLOWED_CENTS;
import static com.ntros.simulation.model.Side.BUY;
import static com.ntros.simulation.model.Side.SELL;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.control.CancellationToken;
import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Client;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import com.ntros.simulation.stage.AbstractSimulationStage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class OrderingStage extends AbstractSimulationStage {

  private final Random RNG = new Random();
  private static final String ORDERING_STAGE_NAME = "OrderPipeline";

  private final List<Product> products;
  private final List<Client> clients;
  private final List<ReentrantLock> clientLocks;
  private final List<Object> pricingLocks;
  private final LinkedBoundedQueue<Order> seeded;
  private final LinkedBoundedQueue<Order> placements;
  private final AtomicLong settledCount;
  private final Map<Integer, PriceFlow> priceFlows;

  public OrderingStage(SimulationContext context) {
    super(context);
    products = context.availableProducts();
    clients = context.clients();
    clientLocks = context.clientLocks();
    pricingLocks = context.pricingLocks();
    seeded = context.seeded();
    placements = context.placements();
    settledCount = context.settledCount();
    priceFlows = context.priceFlows();
  }

  @Override
  public String getStageName() {
    return ORDERING_STAGE_NAME;
  }

  public void seedPoison() throws InterruptedException {
    var poison = new Order(null, null);
    seeded.put(poison);
  }

  public void placePoison() throws InterruptedException {
    var poison = new Order(null, null);
    placements.put(poison);
  }

  public Runnable seeding(CancellationToken cancellationToken) {
    return () -> {
      while (!cancellationToken.isCancelled()) {
        // get market products as list from set

        // init order and add to seeded store
        var client = clients.get(RNG.nextInt(clients.size()));
        var side = RNG.nextFloat() < 0.50f ? BUY : SELL;
        int productsToBuy = 1;
        //          productReservationChance(0.99f, MIN_PRODUCT_SEEDING_BOUND,
        // MAX_PRODUCT_SEEDING_BOUND);
        Set<Product> validatedProducts = new HashSet<>();
        // fix side bias by seeding sell orders from client's portfolio
        if (side == BUY) {
          // product creation chance: x% of the time, an order with only 1 product will be generated

          // generate random products
          while (validatedProducts.size() < productsToBuy) {
            validatedProducts.add(products.get(RNG.nextInt(products.size())));
          }
        } else { // SELLS
          List<Product> owned;
          ReentrantLock clientLock = clientLocks.get(client.getId() - 1);
          clientLock.lock();
          try {
            owned = new ArrayList<>(client.getAccount().getPortfolio().getProducts());
          } finally {
            clientLock.unlock();
          }
          if (owned.isEmpty()) {
            continue;
          } else {
            // add only one sell order per cycle
            validatedProducts.add(owned.get(RNG.nextInt(owned.size())));
          }
        }

        Order order = new Order(client, side);
        order.addAllProducts(new ArrayList<>(validatedProducts));
        try {
          seeded.put(order);
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
          order = seeded.take();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
        if (order == null) {
          continue;
        }
        // poison pill check, any placer receiving a null client will exit before placing a new
        // order
        if (order.getClient() == null) {
          break;
        }
        // get market products as list from set
        List<Product> orderedProducts = order.getProducts();
        var client = order.getClient();

        // get prices, total
        Map<Integer, Long> prices = new HashMap<>();
        long totalPrice = 0L;
        for (var product : orderedProducts) {
          synchronized (pricingLocks.get(product.getId() - 1)) {
            // TODO: add fee
            prices.put(product.getId(), product.getPrice());
            totalPrice += product.getPrice();
          }
        }

        var side = order.getSide();
        int clientIdx = order.getClient().getId() - 1;
        var validatedOrder = new Order(client, side);
        // for [BUY] orders, check if client has enough money
        // for [SELL] orders, if client owns the product
        ReentrantLock clientLock = clientLocks.get(clientIdx);
        clientLock.lock();
        try {
          if (side.equals(BUY)) {
            long availableBalance = client.getAccount().getAvailableBalance();
            // try full buy
            if (availableBalance > totalPrice
                && availableBalance - totalPrice >= MIN_ALLOWED_CENTS) {
              client.getAccount().decreaseAvailableBalance(totalPrice);
              client.getAccount().increaseReservedBalance(totalPrice);
              validatedOrder.addAllProducts(orderedProducts);
            } else {
              // try partial buy
              long partialPrice = 0L;
              for (var ordered : orderedProducts) {
                long nextPrice = partialPrice + ordered.getPrice();
                if (nextPrice <= availableBalance
                    && availableBalance - nextPrice >= MIN_ALLOWED_CENTS) {
                  partialPrice = nextPrice;
                  validatedOrder.addProduct(ordered);
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
              client.getAccount().decreaseAvailableBalance(partialPrice);
              client.getAccount().increaseReservedBalance(partialPrice);
            }
          } else {
            // check ordered against owned, only allow once who match
            List<Product> ownedProducts = client.getAccount().getPortfolio().getProducts();
            for (var ordered : orderedProducts) {
              if (ownedProducts.contains(ordered)) {
                client
                    .getAccount()
                    .removeFromPortfolio(
                        ordered); // reserve by removing, same as balance for BUY order
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

  public Runnable settling() {
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
        if (order.getClient() == null) {
          break;
        }

        if (order.getProducts().isEmpty()) {
          continue;
        }
        // structure ensures indices are always clientId - 1
        int clientIdx = order.getClient().getId() - 1;
        // modify account balance
        ReentrantLock clientLock = clientLocks.get(clientIdx);
        clientLock.lock();
        try {
          Account account = order.getClient().getAccount();
          if (order.side().equals(BUY)) {
            account.decreaseReservedBalance(order.getOrderPrice());
            order.getProducts().forEach(account::addToPortfolio);
          } else {
            // portfolio already updated at placement
            account.increaseAvailableBalance(order.getOrderPrice());
          }
        } finally {
          clientLock.unlock();
        }
        settledCount.incrementAndGet();

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
