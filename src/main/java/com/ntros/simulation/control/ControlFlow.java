package com.ntros.simulation.control;

public final class ControlFlow {

  private volatile boolean placerRunning;
  private volatile boolean executorRunning;
  private volatile boolean pricerRunning;

  public ControlFlow(boolean placerRunning, boolean executorRunning, boolean pricerRunning) {
    this.placerRunning = placerRunning;
    this.executorRunning = executorRunning;
    this.pricerRunning = pricerRunning;
  }


  // shutdown placer

  ///  GET AND SET
  public boolean isPlacerRunning() {
    return placerRunning;
  }

  public void setPlacerRunning(boolean placerRunning) {
    this.placerRunning = placerRunning;
  }

  public boolean isExecutorRunning() {
    return executorRunning;
  }

  public void setExecutorRunning(boolean executorRunning) {
    this.executorRunning = executorRunning;
  }

  public boolean isPricerRunning() {
    return pricerRunning;
  }

  public void setPricerRunning(boolean pricerRunning) {
    this.pricerRunning = pricerRunning;
  }
}
