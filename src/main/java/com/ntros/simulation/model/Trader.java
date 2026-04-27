package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.Objects;

public class Trader {

  private final int id;
  private final Account account;

  public Trader(long initialBuyingPower) {
    id = IdSequencer.nextTraderId();
    account = new Account(initialBuyingPower);
  }

  public int getId() {
    return id;
  }

  public Account getAccount() {
    return account;
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
