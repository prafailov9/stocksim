package com.ntros.simulation.control;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.SimulationSettings;
import com.ntros.simulation.stage.impl.EconomyManager;
import com.ntros.simulation.stage.impl.MarketTickerBoard;
import com.ntros.simulation.stage.impl.OrderPipeline;
import com.ntros.simulation.stage.impl.PricingCycle;

public class SimulationControl implements Control {
  private final OrderPipeline ordering;

  private final Thread[] generators;
  private final Thread[] placers;
  private final Thread[] processors;
  private final Thread[] pricers;
  private final Thread ticker;
  private final Thread economist;

  private final CancellationToken token = new CancellationToken();
  private final SimulationSettings settings;

  public SimulationControl(SimulationSettings settings, SimulationContext context) {
    this.settings = settings;
    ordering = new OrderPipeline(context);

    // init threads
    generators = new Thread[settings.generators()];
    for (int i = 0; i < settings.generators(); i++) {
      generators[i] = new Thread(ordering.generateOrder(token), "t-generator-" + i);
    }

    placers = new Thread[settings.placers()];
    for (int i = 0; i < settings.placers(); i++) {
      placers[i] = new Thread(ordering.placeOrder(), "t-placer-" + i);
    }

    processors = new Thread[settings.processors()];
    for (int i = 0; i < settings.processors(); i++) {
      processors[i] = new Thread(ordering.processOrder(), "t-processor-" + i);
    }

    pricers = new Thread[settings.pricers()];
    for (int i = 0; i < settings.pricers(); i++) {
      pricers[i] =
          new Thread(
              new PricingCycle(context).updatePrices(token, context.priceFlowPartitions().get(i)),
              "t-pricer-" + i);
    }

    ticker = new Thread(new MarketTickerBoard(context).displayMarketState(), "t-ticker-1");
    economist = new Thread(new EconomyManager(context).manageEconomy(), "t-economist-1");
  }

  @Override
  public void start() {
    for (var t : generators) t.start();
    for (var t : placers) t.start();
    for (var t : processors) t.start();
    for (var t : pricers) t.start();
    ticker.start();
    economist.start();
  }

  @Override
  public void stop() {
    token.cancel();
    stopOrdering();
    stopPricing();
    stopEconomy();
    stopMarketDisplay();
  }

  /// 1. Shutdown Ordering Stage
  private void stopOrdering() {
    for (var t : generators) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    // send poison pill to placers
    for (int i = 0; i < settings.placers(); i++) {
      try {
        ordering.poisonPlacers();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // wait for still-active producers to finish
    for (var t : placers) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // once producer joining is done, nothing writes to the queue anymore: send poison pill
    for (int i = 0; i < settings.processors(); i++) {
      try {
        ordering.poisonProcessors();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // wait for still-consumers to drain the queue and exit
    for (var t : processors) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /// 2. Shutdown pricers
  // interrupting as a separate pass ensures all pricers get the interrupt signal at roughly the
  // same time and can finish their current pricing cycles in parallel.
  private void stopPricing() {
    for (var t : pricers) {
      t.interrupt();
    }
    for (var t : pricers) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void stopEconomy() {
    economist.interrupt();
    try {
      economist.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void stopMarketDisplay() {
    ticker.interrupt();
    try {
      ticker.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
