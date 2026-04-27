package com.ntros.simulation.control;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.SimulationSettings;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.stage.impl.EconomyManagementStage;
import com.ntros.simulation.stage.impl.MarketDisplayStage;
import com.ntros.simulation.stage.impl.OrderingStage;
import com.ntros.simulation.stage.impl.PricingStage;

public class SimulationControl implements Control {
  private final OrderingStage ordering;
  private final PricingStage pricing;
  private final MarketDisplayStage display;
  private final EconomyManagementStage economy;

  private final Thread[] seeders;
  private final Thread[] placers;
  private final Thread[] settlers;
  private final Thread[] pricers;
  private final Thread ticker;
  private final Thread economist;

  private final CancellationToken token = new CancellationToken();
  private final SimulationContext context;
  private final SimulationSettings settings;

  public SimulationControl(SimulationSettings settings, SimulationContext context) {
    this.settings = settings;
    this.context = context;

    // init stages
    ordering = new OrderingStage(context);
    pricing = new PricingStage(context);
    display = new MarketDisplayStage(context);
    economy = new EconomyManagementStage(context);

    // init threads
    seeders = new Thread[settings.seeders()];
    for (int i = 0; i < settings.seeders(); i++) {
      seeders[i] = new Thread(ordering.seeding(token), "t-seeder-" + i);
    }

    placers = new Thread[settings.placers()];
    for (int i = 0; i < settings.placers(); i++) {
      placers[i] = new Thread(ordering.placing(), "t-placer-" + i);
    }

    settlers = new Thread[settings.settlers()];
    for (int i = 0; i < settings.settlers(); i++) {
      settlers[i] = new Thread(ordering.settling(), "t-settler-" + i);
    }

    pricers = new Thread[settings.pricers()];
    for (int i = 0; i < settings.pricers(); i++) {
      pricers[i] =
          new Thread(
              pricing.updatePrices(token, context.priceFlowPartitions().get(i)), "t-pricer-" + i);
    }

    ticker = new Thread(display.displayMarketState(), "t-ticker-0");
    economist = new Thread(economy.manageEconomy(), "t-economist-0");
  }

  @Override
  public void start() {
    for (var t : seeders) t.start();
    for (var t : placers) t.start();
    for (var t : settlers) t.start();
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
    for (var t : seeders) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    // send poison pill to placers
    for (int i = 0; i < settings.placers(); i++) {
      try {
        ordering.seedPoison();
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
    for (int i = 0; i < settings.settlers(); i++) {
      try {
        ordering.placePoison();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // wait for still-consumers to drain the queue and exit
    for (var t : settlers) {
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
