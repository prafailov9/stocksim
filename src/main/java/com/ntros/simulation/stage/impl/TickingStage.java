package com.ntros.simulation.stage.impl;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.model.Money;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.queue.BoundedMinHeap;
import com.ntros.simulation.stage.AbstractSimulationStage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class TickingStage extends AbstractSimulationStage {
  private static final int TICKER_TIMESTEP_MS = 2000;
  private static final String NAME = "MarketDisplayStage";
  private final BoundedMinHeap<PriceFlow> topMovers;
  private final AtomicLong settledCount;

  public TickingStage(SimulationContext context) {
    super(context);
    topMovers = context.topMovers();
    settledCount = context.settledCount();
  }

  @Override
  public String getStageName() {
    return NAME;
  }

  public Runnable displayMarketState() {
    return () -> {
      while (true) {
        try {
          Thread.sleep(TICKER_TIMESTEP_MS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }

        // drain top movers
        List<PriceFlow> movers = new ArrayList<>();
        int size = topMovers.size();
        for (int i = 0; i < size; i++) {
          movers.add(topMovers.poll());
        }
        if (movers.isEmpty()) {
          continue;
        }

        // sort descending by |delta| for display
        movers.sort((a, b) -> Long.compare(Math.abs(b.getDelta()), Math.abs(a.getDelta())));
        // deduplicate by product code, keep first (highest delta) occurrence
        Set<String> seen = new HashSet<>();
        List<PriceFlow> unique = new ArrayList<>();
        for (var mover : movers) {
          if (seen.add(mover.getCode())) {
            unique.add(mover);
          }
        }
        // clear terminal
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // header
        String timestamp =
            java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.printf(
            "  %-6s  %-12s  %-12s  %-10s  %-6s  %-6s  %s%n",
            "CODE", "PRICE", "DELTA", "MOVE %", "BUYS", "SELLS", "LAST UPDATE: " + timestamp);
        System.out.println("  " + "─".repeat(72));

        // rows
        for (var mover : unique) {
          long delta = mover.getDelta();
          long price = mover.getCurrentPrice();

          String arrow = delta > 0 ? "↑" : delta < 0 ? "↓" : "─";
          String ansi = delta > 0 ? "\033[32m" : delta < 0 ? "\033[31m" : "\033[37m";
          String reset = "\033[0m";

          // percentage move relative to price before delta was applied
          long prevPrice = price - delta;
          double movePct = prevPrice != 0 ? (delta * 100.0) / prevPrice : 0.0;

          System.out.printf(
              ansi + "  %-6s  %-12s  %-12s  %-10s  %-6d  %-6d  %s" + reset + "%n",
              mover.getCode(),
              Money.bucks(price),
              Money.bucks(Math.abs(delta)),
              String.format("%s%.2f%%", delta >= 0 ? "+" : "-", Math.abs(movePct)),
              mover.getBuys(),
              mover.getSells(),
              arrow);
        }

        // footer
        System.out.printf("%n  Total settled orders: %d%n", settledCount.get());
      }
    };
  }
}
