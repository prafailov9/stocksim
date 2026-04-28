package com.ntros.simulation.model;

import com.ntros.simulation.queue.BoundedMinHeap;
import java.util.Comparator;
import java.util.List;

public class MarketSnapshot {

  // highest positive delta at any cycle
  private final BoundedMinHeap<PriceFlow> topGainers;
  // highest negative delta at any cycle
  private final BoundedMinHeap<PriceFlow> topLosers;

  public MarketSnapshot(int gainersCap, int losersCap) {
    this.topGainers =
        new BoundedMinHeap<>(gainersCap, Comparator.comparingLong(PriceFlow::getDelta));
    this.topLosers =
        new BoundedMinHeap<>(gainersCap, Comparator.comparingLong(PriceFlow::getDelta));
  }

  public void offerGainer(PriceFlow priceFlow) {
    topGainers.offer(priceFlow);
  }

  public void offerLoser(PriceFlow priceFlow) {
    topLosers.offer(priceFlow);
  }

  // read-only getters
  public List<PriceFlow> getGainers() {
    return topGainers.toList();
  }

  public List<PriceFlow> getLosers() {
    return topLosers.toList();
  }
}
