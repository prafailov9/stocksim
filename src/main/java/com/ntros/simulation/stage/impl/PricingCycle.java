package com.ntros.simulation.stage.impl;

import static com.ntros.MarketUtils.MAX_PRICE_MOVE_PCT_CENTS;
import static com.ntros.MarketUtils.MIN_PRODUCT_PRICE;
import static com.ntros.MarketUtils.PRICE_SENSITIVITY_CENTS;

import com.ntros.simulation.SimulationContext;
import com.ntros.simulation.control.CancellationToken;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.queue.BoundedMinHeap;
import com.ntros.simulation.stage.AbstractSimulationStage;
import java.util.List;
import java.util.Map;

public class PricingCycle extends AbstractSimulationStage {
  private static final long PRICER_TIMESTEP_MS = 500;
  private final List<Object> pricingLocks;
  private final List<Product> availableProducts;
  private final BoundedMinHeap<PriceFlow> topMovers;

  public PricingCycle(SimulationContext context) {
    super(context);
    pricingLocks = context.pricingLocks();
    availableProducts = context.availableProducts();
    topMovers = context.topMovers();
  }

  public Runnable updatePrices(
      CancellationToken cancellationToken, Map<Integer, PriceFlow> partition) {
    return () -> {
      while (!cancellationToken.isCancelled()) {
        /// arbitrary time window for price fluctuation.
        try {
          Thread.sleep(PRICER_TIMESTEP_MS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt(); // restore interrupt flag
          break;
        }

        for (var entry : partition.entrySet()) {
          int productIdx = entry.getKey() - 1;
          var flow = entry.getValue();

          synchronized (pricingLocks.get(productIdx)) {
            long buys = flow.getBuys();
            long sells = flow.getSells();
            long totalVolume = buys + sells;
            long distance = buys - sells;

            flow.resetBuys();
            flow.resetSells();

            // skip pricing for this product if no orders have been executed this cycle
            if (totalVolume == 0) {
              continue;
            }

            var product = availableProducts.get(productIdx);
            double distanceRatio = (double) distance / totalVolume;
            double movePct = distanceRatio * PRICE_SENSITIVITY_CENTS;
            movePct =
                Math.max(-MAX_PRICE_MOVE_PCT_CENTS, Math.min(MAX_PRICE_MOVE_PCT_CENTS, movePct));

            long oldPrice = product.getPrice();
            long delta = Math.round(oldPrice * movePct / 100.0);
            long newPrice = Math.max(oldPrice + delta, MIN_PRODUCT_PRICE);
            product.setPrice(newPrice);
            flow.setDelta(delta);
            flow.setCurrentPrice(newPrice);
            topMovers.offer(flow.snapshot(buys, sells));
            if (delta > 0) {
              context.marketSnapshot().offerGainer(flow);
            } else {
              context.marketSnapshot().offerLoser(flow);
            }
          }
        }
      }
    };
  }
}
