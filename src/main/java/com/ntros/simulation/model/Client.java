package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.Objects;

public class Client {

  private final int id;
  private final Account account;

  public Client(long initialBuyingPower) {
    id = IdSequencer.nextClientId();
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
    if (!(o instanceof Client client)) return false;
    return id == client.id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
