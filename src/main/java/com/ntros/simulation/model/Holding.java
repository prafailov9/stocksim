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

  public void addShares(long quantity, long pricePerShare) {
    long totalCost = (this.quantity * this.avgCost) + (quantity * pricePerShare);
    this.quantity += quantity;
    this.avgCost = totalCost / this.quantity;
  }

  public void removeShares(long quantity) {
    if (quantity > this.quantity) {
      throw new IllegalArgumentException("Cannot sell more shares than owned");
    }
    this.quantity -= quantity;
  }
}
