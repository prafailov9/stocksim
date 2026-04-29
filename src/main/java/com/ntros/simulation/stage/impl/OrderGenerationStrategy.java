package com.ntros.simulation.stage.impl;

import static com.ntros.simulation.model.Side.BUY;
import static com.ntros.simulation.model.Side.SELL;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.model.Holding;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.model.Side;
import com.ntros.simulation.model.Trader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class OrderGenerationStrategy {
  private final Random RNG = new Random();
  private static final float BUY_CHANCE = 0.50f;
  private static final long QUANTITY_MAX = 100;

  private final SimulationContext context;

  private final List<Product> products;
  private final List<ReentrantLock> traderLocks;

  public OrderGenerationStrategy(SimulationContext context) {
    this.context = context;

    products = context.availableProducts();
    traderLocks = context.traderLocks();
  }

  // random generation
  //  public Order noiseStrat(Trader trader) {
  //    var side = RNG.nextFloat() < 0.50f ? BUY : SELL;
  //    Set<Product> generatedProducts = new HashSet<>();
  //    long generatedQuantity;
  //
  //    // single product each pass for simplicity, maybe expand later
  //    if (side == BUY) {
  //      var product = products.get(RNG.nextInt(products.size()));
  //      generatedProducts.add(product);
  //      // determine qty from trader's current spending balance
  //      long price = product.getPrice();
  //      long affordableShares = trader.getAccount().getAvailableBalance() / price;
  //      // cap at 100;
  //      generatedQuantity =
  //          affordableShares > 0 ? RNG.nextLong(1, Math.min(affordableShares, QUANTITY_MAX) + 1) :
  // 1;
  //    } else { // SELLS
  //      Map<Product, Holding> ownedHoldings;
  //      List<Product> ownedProducts;
  //      ReentrantLock traderLock = traderLocks.get(trader.getId() - 1);
  //      traderLock.lock();
  //      try {
  //        var holdings = trader.getAccount().getPortfolio().getHoldings();
  //        // if trader owns nothing - skip
  //        if (holdings.isEmpty()) {
  //          return null;
  //        }
  //        // copy
  //        ownedHoldings = new HashMap<>(holdings);
  //        ownedProducts = new ArrayList<>(ownedHoldings.keySet());
  //      } finally {
  //        traderLock.unlock();
  //      }
  //
  //      // add only one single-product, sell order per cycle, with varying quantities to sell,
  //      // avg less than 1/3 of total holding
  //
  //      var randomProduct = ownedProducts.get(RNG.nextInt(ownedProducts.size()));
  //      var holding = trader.getAccount().getPortfolio().getHoldings().get(randomProduct);
  //      // force 1-qty sell
  //      long ownedQuantity = holding != null ? holding.getQuantity() : 0;
  //
  //      var sellQtyCap = Math.max(1L, ownedQuantity / 4L);
  //      var qtyToSell = RNG.nextLong(1, sellQtyCap + 1);
  //
  //      // select random holding
  //      generatedProducts.add(randomProduct);
  //      generatedQuantity = qtyToSell;
  //    }
  //    Order order = new Order(trader, side);
  //    order.addAllProducts(new ArrayList<>(generatedProducts));
  //    order.setQuantity(generatedQuantity);
  //    return order;
  //  }
  public Order noiseStrat(Trader trader) {
    var side = RNG.nextFloat() < 0.50f ? BUY : SELL;

    if (side == BUY) {
      var product = products.get(RNG.nextInt(products.size()));
      // balance read is a hint — placer validates under lock
      long affordableShares = trader.getAccount().getAvailableBalance() / product.getPrice();
      long qty =
          affordableShares > 0 ? RNG.nextLong(1, Math.min(affordableShares, QUANTITY_MAX) + 1) : 1;
      Order order = new Order(trader, BUY);
      order.addProduct(product);
      order.setQuantity(qty);
      return order;
    } else {
      Map<Product, Holding> snapshot;
      List<Product> ownedProducts;
      ReentrantLock traderLock = traderLocks.get(trader.getId() - 1);
      traderLock.lock();
      try {
        var holdings = trader.getAccount().getPortfolio().getHoldings();
        if (holdings.isEmpty()) return null;
        snapshot = new HashMap<>(holdings);
        ownedProducts = new ArrayList<>(holdings.keySet());
      } finally {
        traderLock.unlock();
      }

      var randomProduct = ownedProducts.get(RNG.nextInt(ownedProducts.size()));
      long ownedQty = snapshot.getOrDefault(randomProduct, new Holding(0, 0)).getQuantity();
      // qty is a hint — placer will clamp to actual current quantity under lock
      long sellQtyCap = Math.max(1L, ownedQty / 4L);
      long qty = RNG.nextLong(1, sellQtyCap + 1);

      Order order = new Order(trader, SELL);
      order.addProduct(randomProduct);
      order.setQuantity(qty);
      return order;
    }
  }

  // traders follow high-moving stock
  public Order momentumStrat(Trader trader) {
    Side side = rollOrderSide();

    Product productForOrder;
    long quantityForOrder;
    if (side.equals(BUY)) {
      // contains highest deltas for current pricing cycle
      List<PriceFlow> topGainers = context.marketSnapshot().getGainers();

      // fallback to random generation if no high-moving products this cycle
      if (topGainers.isEmpty()) {
        return noiseStrat(trader);
      }

      // highest delta is at the end of the heap
      int idx = RNG.nextInt(topGainers.size());
      PriceFlow topFlow = topGainers.get(idx);
      productForOrder = context.availableProducts().get(topFlow.getProductId() - 1);
      // shares to buy
      long affordableShares =
          trader.getAccount().getAvailableBalance() / productForOrder.getPrice();
      quantityForOrder =
          generateQuantityToBuy(
              affordableShares, Math.abs(topFlow.getDelta()), topFlow.getCurrentPrice());
    } else { // SELL branch
      ReentrantLock traderLock = traderLocks.get(trader.getId() - 1);
      traderLock.lock();
      Map<Product, Holding> ownedSnapshot;
      try {
        ownedSnapshot = new HashMap<>(trader.getAccount().getPortfolio().getHoldings());
      } finally {
        traderLock.unlock();
      }
      if (ownedSnapshot.isEmpty()) {
        return null;
      }

      // 30% chance of profit-taking sell regardless of trend
      if (RNG.nextFloat() < 0.30f) {
        // find the holding with the highest unrealized gain
        // sell half of it
        Product bestGainer =
            ownedSnapshot.entrySet().stream()
                .filter(e -> e.getValue().getAvgCost() > 0)
                .filter(e -> e.getKey().getPrice() > e.getValue().getAvgCost())
                .max(
                    Comparator.comparingLong(
                        e -> e.getKey().getPrice() - e.getValue().getAvgCost()))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestGainer != null) {
          long qty = Math.max(1, ownedSnapshot.get(bestGainer).getQuantity() / 2);
          Order order = new Order(trader, SELL);
          order.addProduct(bestGainer);
          order.setQuantity(qty);
          return order;
        }
      }

      // get the current market losers snapshot
      List<PriceFlow> losers = context.marketSnapshot().getLosers();

      // find owned products that are also market losers, sorted by worst performer first
      // losers list is already sorted by delta ascending (most negative first)
      Product productToSell = null;
      for (PriceFlow loserFlow : losers) {
        // find a matching product in this trader's portfolio
        Product candidate =
            ownedSnapshot.keySet().stream()
                .filter(p -> p.getId() == loserFlow.getProductId())
                .findFirst()
                .orElse(null);
        if (candidate != null) {
          productToSell = candidate;
          break; // take the worst-performing owned product
        }
      }

      if (productToSell == null) {
        // trader owns nothing that's currently losing
        // 80% chance to hold, 20% chance to sell a random holding anyway
        if (RNG.nextFloat() > 0.20f) {
          return null;
        }
        List<Product> ownedList = new ArrayList<>(ownedSnapshot.keySet());
        productToSell = ownedList.get(RNG.nextInt(ownedList.size()));
      }

      // momentum traders sell aggressively — up to half the position
      long currentQty = ownedSnapshot.get(productToSell).getQuantity();
      quantityForOrder = Math.max(1, currentQty / 2);
      productForOrder = productToSell;
    }
    Order order = new Order(trader, side);
    order.addProduct(productForOrder);
    order.setQuantity(quantityForOrder);
    return order;
  }

  public Order longTermStrat(Trader trader) {
    // structural buy bias — 80% buy, 20% sell
    Side side = RNG.nextFloat() < 0.80f ? BUY : SELL;

    if (side.equals(BUY)) {
      // pick random product, buy small fixed quantity (1-5 shares)
      var product = products.get(RNG.nextInt(products.size()));
      long price = product.getPrice();
      long affordableShares = trader.getAccount().getAvailableBalance() / price;
      if (affordableShares == 0) {
        return null;
      }
      long qty = Math.min(affordableShares, RNG.nextLong(1, 6)); // 1-5 shares
      Order order = new Order(trader, BUY);
      order.addProduct(product);
      order.setQuantity(qty);
      return order;
    } else {
      // only sell on significant loss — check holdings for panic threshold
      ReentrantLock lock = traderLocks.get(trader.getId() - 1);
      lock.lock();
      Map<Product, Holding> snapshot;
      try {
        snapshot = new HashMap<>(trader.getAccount().getPortfolio().getHoldings());
      } finally {
        lock.unlock();
      }
      if (snapshot.isEmpty()) {
        return null;
      }

      // find any holding down more than 40% from avg cost
      for (var entry : snapshot.entrySet()) {
        var product = entry.getKey();
        var holding = entry.getValue();
        long avgCost = holding.getAvgCost();
        long currentPrice = product.getPrice();
        if (avgCost > 0 && currentPrice < avgCost * 0.60) {
          // panic sell - full exit
          Order order = new Order(trader, SELL);
          order.addProduct(product);
          order.setQuantity(holding.getQuantity());
          return order;
        }
      }
      return null; // no panic threshold hit - hold
    }
  }

  private long generateQuantityToBuy(
      long affordableShares, long absDelta, long currentProductPrice) {
    // scale buy size: stronger trend = larger fraction of affordable shares
    double trendStrength = (double) absDelta / currentProductPrice; // 0.0 to ~0.03
    double fraction = 0.1 + (trendStrength * 10); // 10% to 40% of affordable
    return Math.max(1, Math.min(affordableShares, Math.round(affordableShares * fraction)));
  }

  private Side rollOrderSide() {
    return RNG.nextFloat() < BUY_CHANCE ? BUY : SELL;
  }
}
