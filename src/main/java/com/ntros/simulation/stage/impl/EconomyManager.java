package com.ntros.simulation.stage.impl;

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
  private static final int BALANCE_CHECK_TIMESTEP_MS = 1000;
  private static final long BALANCE_FLOOR_CENTS = 5_000_000L;
  private static final long BALANCE_INJECTION_CENTS = 10_000_000L;

  private final List<Product> availableProducts;
  private final List<Trader> traders;
  private final List<ReentrantLock> clientLocks;

  public EconomyManager(SimulationContext context) {
    super(context);
    availableProducts = context.availableProducts();
    traders = context.traders();
    clientLocks = context.clientLocks();
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
          ReentrantLock lock = clientLocks.get(i);
          // skip if settler is using this client right now
          if (!lock.tryLock()) {
            continue;
          }
          try {
            Account account = traders.get(i).getAccount();
            if (account.getAvailableBalance() < BALANCE_FLOOR_CENTS) {
              account.increaseAvailableBalance(BALANCE_INJECTION_CENTS);
            }
            if (account.getPortfolio().getHoldings().isEmpty()) {
              int count = RNG.nextInt(1, 6);
              for (int j = 0; j < count; j++) {

                account.getPortfolio().addHolding(
                    availableProducts.get(RNG.nextInt(availableProducts.size())), 1);
              }
            }
          } finally {
            lock.unlock();
          }
        }
      }
    };
  }
}
