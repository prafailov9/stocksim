package com.ntros.simulation;

import java.util.concurrent.atomic.AtomicInteger;

public class IdSequencer {

  private static final AtomicInteger MARKET_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger PRODUCT_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger ORDER_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger TRADER_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger ACCOUNT_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger PORTFOLIO_ID_COUNTER = new AtomicInteger(1);
  private static final AtomicInteger ORDER_FLOW_ID_COUNTER = new AtomicInteger(1);


  public static int nextMarketId() {
    return MARKET_ID_COUNTER.getAndIncrement();
  }

  public static int nextProductId() {
    return PRODUCT_ID_COUNTER.getAndIncrement();
  }

  public static int nextOrderId() {
    return ORDER_ID_COUNTER.getAndIncrement();
  }

  public static int nextTraderId() {
    return TRADER_ID_COUNTER.getAndIncrement();
  }

  public static int nextAccountId() {
    return ACCOUNT_ID_COUNTER.getAndIncrement();
  }

  public static int nextPortfolioId() {
    return PORTFOLIO_ID_COUNTER.getAndIncrement();
  }

  public static int nextOrderFlowId() {
    return ORDER_FLOW_ID_COUNTER.getAndIncrement();
  }
}
