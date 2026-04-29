package com.ntros.simulation.stage.impl;

import com.ntros.InitialWealthTier;
import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.stage.AbstractSimulationStage;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class EconomyManager extends AbstractSimulationStage {
  private final Random RNG = new Random();
  private static final int BALANCE_CHECK_TIMESTEP_MS = 100;
  private static final long BALANCE_FLOOR_CENTS = 500_000L;
  private static final long BALANCE_INJECTION_CENTS = 10_000_000L;

  private final List<Product> availableProducts;
  private final List<Trader> traders;
  private final List<ReentrantLock> traderLocks;

  public EconomyManager(SimulationContext context) {
    super(context);
    availableProducts = context.availableProducts();
    traders = context.traders();
    traderLocks = context.traderLocks();
  }

  public Runnable manageEconomy() {
    return () -> {
      while (true) {
        try {
          Thread.sleep(BALANCE_CHECK_TIMESTEP_MS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
        for (int i = 0; i < traders.size(); i++) {
          ReentrantLock lock = traderLocks.get(i);
          // skip if processor is using this trader right now
          if (!lock.tryLock()) {
            continue;
          }
          try {
            var trader = traders.get(i);
            Account account = traders.get(i).getAccount();
            // balance injection based off of trader's initial balance
            long floor =
                (long) (account.getInitialBalance() * tierFloorFraction(trader.getWealthTier()));
            if (account.getAvailableBalance() < floor) {
              account.increaseAvailableBalance(BALANCE_INJECTION_CENTS);
            }
            if (account.getPortfolio().getHoldings().isEmpty()) {
              int count = RNG.nextInt(1, 6);
              for (int j = 0; j < count; j++) {

                account
                    .getPortfolio()
                    .addHolding(availableProducts.get(RNG.nextInt(availableProducts.size())), 1);
              }
            }
          } finally {
            lock.unlock();
          }
        }
      }
    };
  }

  private float tierFloorFraction(InitialWealthTier tier) {
    return switch (tier) {
      case SMALL -> 0.30f; // always keep 30% of starting balance
      case REGULAR -> 0.20f;
      case AFFLUENT -> 0.15f;
      case HIGH_NET_WORTH -> 0.10f;
      case WHALE -> 0.05f;
    };
  }
}
