package com.ntros.simulation;

import com.ntros.simulation.control.SimulationControl;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.model.Market;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.queue.BoundedMinHeap;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class StockMarketSimulation {

  private static final int MAX_ALLOWED_ORDERS = 10_000_000;
  private static final int MAX_TOP_MOVERS = 10;

  private static final int GENERATORS = 3;
  private static final int PLACERS = 3;
  private static final int PROCESSORS = 3;
  private static final int PRICERS = 3;

  private final SimulationControl control;

  public StockMarketSimulation(Market market, List<Trader> traders) {
    // data
    List<Product> availableProducts = new ArrayList<>(market.getAvailableProducts());
    // TODO: move to lock stripping
    List<ReentrantLock> clientLocks = new ArrayList<>();
    List<Object> pricingLocks = new ArrayList<>();
    // init locks
    traders.forEach(x -> clientLocks.add(new ReentrantLock()));
    availableProducts.forEach(p -> pricingLocks.add(new Object()));

    // init price flows
    Map<Integer, PriceFlow> priceFlows = new HashMap<>();
    for (var p : availableProducts) {
      priceFlows.put(p.getId(), new PriceFlow(p.getId(), p.getCode(), p.getPrice()));
    }

    // partition products between pricers
    List<Map<Integer, PriceFlow>> priceFlowPartitions = new ArrayList<>();
    for (int i = 0; i < PRICERS; i++) {
      priceFlowPartitions.add(new HashMap<>());
    }

    int x = 0;
    for (var entry : priceFlows.entrySet()) {
      priceFlowPartitions.get(x % PRICERS).put(entry.getKey(), entry.getValue());
      x++;
    }

    // TODO: add storage at some point
    var settings = new SimulationSettings(GENERATORS, PLACERS, PROCESSORS, PRICERS, false);

    var context =
        new SimulationContext(
            traders,
            availableProducts,
            priceFlows,
            priceFlowPartitions,
            clientLocks,
            pricingLocks,
            new LinkedBoundedQueue<>(MAX_ALLOWED_ORDERS),
            new LinkedBoundedQueue<>(MAX_ALLOWED_ORDERS),
            new BoundedMinHeap<>(
                MAX_TOP_MOVERS, Comparator.comparingLong(flow -> Math.abs(flow.getDelta()))),
            new AtomicLong(0));

    control = new SimulationControl(settings, context);
  }

  public void run() {
    control.start();
  }

  public void stop() {
    control.stop();
  }
}
