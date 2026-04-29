package com.ntros.simulation.model;

import com.ntros.WealthTier;
import com.ntros.simulation.IdSequencer;
import java.util.Objects;

public class Trader {

  private final int id;
  private final Account account;
  // indicates the initial wealth tier of the trader, pre-sim start. Would be interesting to compare
  // with post-sim results, if the trader went broke or got rich
  private WealthTier wealthTier;
  private final TraderType traderType; // NEW

  public Trader(long initialBuyingPower, WealthTier wealthTier, TraderType traderType) {
    id = IdSequencer.nextTraderId();
    account = new Account(initialBuyingPower);
    this.wealthTier = wealthTier;
    this.traderType = traderType;
  }

  public int getId() {
    return id;
  }

  public TraderType getTraderType() {
    return traderType;
  }

  public Account getAccount() {
    return account;
  }

  public WealthTier getWealthTier() {
    return wealthTier;
  }

  public void setWealthTier(WealthTier tier) {
    this.wealthTier = tier;
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
