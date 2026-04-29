package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.HashMap;

public class Account {

  private final int id;
  private long initialBalance;
  private long availableBalance;
  private long reservedBalance;
  private final Portfolio portfolio;

  public Account(long availableBalance) {
    this.id = IdSequencer.nextAccountId();
    this.availableBalance = availableBalance;
    this.initialBalance = availableBalance;
    this.portfolio = new Portfolio(id, new HashMap<>());
  }

  public Portfolio getPortfolio() {
    return portfolio;
  }

  public int getId() {
    return id;
  }

  public long getInitialBalance() { return initialBalance; }

  public long getTotalBalance() {
    return availableBalance + reservedBalance;
  }

  public long getAvailableBalance() {
    return availableBalance;
  }

  public void decreaseAvailableBalance(long amount) {
    availableBalance -= amount;
  }

  public void increaseAvailableBalance(long amount) {
    availableBalance += amount;
  }

  public long getReservedBalance() {
    return reservedBalance;
  }

  public void decreaseReservedBalance(long amount) {
    reservedBalance -= amount;
  }

  public void increaseReservedBalance(long amount) {
    reservedBalance += amount;
  }
}
