package com.ntros.simulation.model;

import com.ntros.InitialWealthTier;
import com.ntros.simulation.IdSequencer;
import java.util.Objects;

public class Trader {

  private final int id;
  private final Account account;
  private TradingType tradingType;
  private final InitialWealthTier wealthTier;
  public Trader(long initialBuyingPower, InitialWealthTier wealthTier) {
    id = IdSequencer.nextTraderId();
    account = new Account(initialBuyingPower);
    this.wealthTier = wealthTier;
  }

  public int getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public TradingType getTradingType() {
    return tradingType;
  }

  public InitialWealthTier getWealthTier() {
    return wealthTier;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Trader trader)) return false;
    return id == trader.id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
