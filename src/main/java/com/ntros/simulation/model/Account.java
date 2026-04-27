package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.ArrayList;
import java.util.UUID;

public class Account {

  private final int id;
  private long availableBalance;
  private long reservedBalance;
  private final Portfolio portfolio;

  public Account(long availableBalance) {
    id = IdSequencer.nextAccountId();
    this.availableBalance = availableBalance;
    String portfolioName = "portf-" + UUID.randomUUID().toString().substring(0, 12) + "acc-" + id;
    portfolio = new Portfolio(portfolioName, new ArrayList<>());
  }

  public Portfolio getPortfolio() {
    return portfolio;
  }

  public boolean ownsAsset(Product product) {
    return portfolio.getProducts().contains(product);
  }

  public void addToPortfolio(Product product) {
    portfolio.addProduct(product);
  }

  public void addToPortfolio(Product product, long quantity) {
    portfolio.addProduct(product);
    portfolio.addHolding(product, quantity);
  }

  public void removeFromPortfolio(Product product) {
    if (!portfolio.getProducts().contains(product)) {
      throw new IllegalArgumentException(
          String.format("Account %s does not own this product %s", id, product.getCode()));
    }
    portfolio.removeProduct(product);
  }

  public int getId() {
    return id;
  }

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
