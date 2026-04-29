package com.ntros.simulation.stage.impl;

import static com.ntros.MarketUtils.EXPONENT;

import com.ntros.MarketUtils;
import com.ntros.WealthTier;
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
  private static final long MIN_INJECTION_CENTS = 10_000L;

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
            tryUpdateWealthTier(trader);
            // balance injection based off of trader's initial balance
            Account account = trader.getAccount();
            long totalBalance = account.getTotalBalance();
            long floor = tierFloor(trader.getWealthTier());
            if (totalBalance < floor) {
              long injection = Math.max(floor / 10, MIN_INJECTION_CENTS);
              account.increaseAvailableBalance(injection);
            }
            if (account.getPortfolio().getHoldings().isEmpty()) {
              int count = RNG.nextInt(1, 6);
              for (int j = 0; j < count; j++) {
                account
                    .getPortfolio()
                    .addHolding(availableProducts.get(RNG.nextInt(availableProducts.size())), 5);
              }
            }
          } finally {
            lock.unlock();
          }
        }
      }
    };
  }

  private void tryUpdateWealthTier(Trader trader) {
    var newTier = MarketUtils.determineWealthTier(trader.getAccount().getTotalBalance());
    if (!trader.getWealthTier().equals(newTier)) {
      trader.setWealthTier(newTier);
    }
  }

  private long tierFloor(WealthTier tier) {
    return switch (tier) {
      case SMALL         -> 100   * EXPONENT;   // $100
      case REGULAR       -> 5_000 * EXPONENT;   // $5,000
      case AFFLUENT      -> 75_000 * EXPONENT;  // $75,000
      case HIGH_NET_WORTH-> 500_000 * EXPONENT; // $500,000
      case WHALE         -> 5_000_000 * EXPONENT; // $5,000,000
    };
  }
}
