package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;

public class Holding {
  private final int id = IdSequencer.nextHoldingId();

  private final int productId;
  private long quantity;
  private long avgCost;

  public Holding(int productId) {
    this.productId = productId;
  }

  public Holding(int productId, long quantity) {
    this.productId = productId;
    this.quantity = quantity;
  }

  public int getId() {
    return id;
  }

  public long getQuantity() {
    return quantity;
  }

  public void setQuantity(long quantity) {
    this.quantity = quantity;
  }

  public long getAvgCost() {
    return avgCost;
  }

  public void setAvgCost(long avgCost) {
    this.avgCost = avgCost;
  }
}
