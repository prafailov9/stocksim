package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;

public class PriceFlow {

  private final int id = IdSequencer.nextOrderFlowId();
  private final int productId;
  private final String code;
  private long currentPrice;
  private long buys;
  private long sells;
  private long delta;

  // Noise

  public PriceFlow(int productId, String code, long currentPrice) {
    this.productId = productId;
    this.code = code;
    this.currentPrice = currentPrice;
  }

  public PriceFlow snapshot(long buys, long sells) {
    PriceFlow s = new PriceFlow(productId, code, currentPrice);
    s.buys = buys;
    s.sells = sells;
    s.delta = this.delta;
    s.currentPrice = this.currentPrice;
    return s;
  }

  /// Exe counters
  public void increaseBuys() {
    buys++;
  }

  public void increaseSells() {
    sells++;
  }

  public void resetBuys() {
    buys = 0;
  }

  public void resetSells() {
    sells = 0;
  }

  ///  GETTERS
  public int getId() {
    return id;
  }

  public int getProductId() {
    return productId;
  }

  public String getCode() {
    return code;
  }

  public long getBuys() {
    return buys;
  }

  public long getSells() {
    return sells;
  }

  public long getDelta() {
    return delta;
  }

  public void setDelta(long delta) {
    this.delta = delta;
  }

  public long getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(long price) {
    currentPrice = price;
  }
}


