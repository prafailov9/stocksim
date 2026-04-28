package com.ntros.simulation.stage.impl;

import static com.ntros.simulation.model.Side.BUY;
import static com.ntros.simulation.model.Side.SELL;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.model.Holding;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class OrderGenerationStrategies {
  private final Random RNG = new Random();

  private final SimulationContext context;

  private final List<Product> products;
  private final List<Trader> traders;
  private final List<ReentrantLock> traderLocks;
  private final List<Object> pricingLocks;
  private final LinkedBoundedQueue<Order> generatedOrders;
  private final LinkedBoundedQueue<Order> placements;
  private final AtomicLong processedOrdersCount;
  private final Map<Integer, PriceFlow> priceFlows;

  public OrderGenerationStrategies(SimulationContext context) {
    this.context = context;

    products = context.availableProducts();
    traders = context.traders();
    traderLocks = context.traderLocks();
    pricingLocks = context.pricingLocks();
    generatedOrders = context.generatedOrders();
    placements = context.placements();
    processedOrdersCount = context.processedCount();
    priceFlows = context.priceFlows();
  }

  public Order noiseStrat(Trader trader) {
    var side = RNG.nextFloat() < 0.50f ? BUY : SELL;
    Set<Product> validatedProducts = new HashSet<>();
    long generatedQuantity;

    // single product each pass for simplicity, maybe expand later
    if (side == BUY) {
      var product = products.get(RNG.nextInt(products.size()));
      validatedProducts.add(product);
      // determine qty from trader's current spending balance
      long price = product.getPrice();
      long affordableShares = trader.getAccount().getAvailableBalance() / price;
      // cap at 100;
      generatedQuantity =
          affordableShares > 0 ? RNG.nextLong(1, Math.min(affordableShares, 100) + 1) : 1;
    } else { // SELLS
      Map<Product, Holding> ownedHoldings;
      List<Product> ownedProducts;
      ReentrantLock traderLock = traderLocks.get(trader.getId() - 1);
      traderLock.lock();
      try {
        ownedHoldings = trader.getAccount().getPortfolio().getHoldings();
        ownedProducts = new ArrayList<>(ownedHoldings.keySet());
      } finally {
        traderLock.unlock();
      }
      // if trader owns nothing - skip
      if (ownedHoldings.isEmpty()) {
        return null;
      } else {
        // add only one single-product, sell order per cycle, with varying quantities to sell,
        // avg less than 1/3 of total holding

        var randomProduct = ownedProducts.get(RNG.nextInt(ownedProducts.size()));
        var holding = trader.getAccount().getPortfolio().getHoldings().get(randomProduct);
        long productQuantity = holding != null ? holding.getQuantity() : 0;
        if (productQuantity == 0) return null;

        var sellQtyCap = Math.max(1L, productQuantity / 3L);
        var qtyToSell = RNG.nextLong(1, sellQtyCap + 1);

        // select random holding
        validatedProducts.add(randomProduct);
        generatedQuantity = qtyToSell;
      }
    }
    Order order = new Order(trader, side);
    order.addAllProducts(new ArrayList<>(validatedProducts));
    order.setQuantity(generatedQuantity);
    return order;
  }

  public Order momentumStrat(Trader trader) {
    return null;
  }

  public Order longTermStrat(Trader trader) {
    return null;
  }
}
