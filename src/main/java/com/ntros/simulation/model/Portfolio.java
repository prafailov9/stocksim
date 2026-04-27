package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.Map;
import java.util.UUID;

public class Portfolio {

  private final int id = IdSequencer.nextPortfolioId();
  private final int accountId;
  private final String name;
  private final Map<Product, Holding> holdings;

  public Portfolio(int accountId, Map<Product, Holding> holdings) {
    this.accountId = accountId;
    this.name = "portf-" + UUID.randomUUID().toString().substring(0, 12) + "acc-" + id;
    if (holdings == null) {
      throw new IllegalArgumentException("Holdings cannot be null");
    }
    this.holdings = holdings;
    for (var holding : holdings.entrySet()) {}
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Map<Product, Holding> getHoldings() {
    return holdings;
  }

  public double getTotalValue() {
    return holdings.entrySet().stream()
        .mapToLong(e -> e.getKey().getPrice() * e.getValue().getQuantity())
        .sum();
  }

  public void addHolding(Product product, long quantity) {
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null");
    }
    if (quantity == 0) {
      throw new IllegalArgumentException("Product quantity cannot be 0");
    }

    if (owns(product)) {
      increaseHoldingQuantity(product, quantity);
    } else {
      holdings.put(product, new Holding(product.getId(), quantity));
    }
  }

  public void removeHolding(Product product) {
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null");
    }
    holdings.remove(product);
  }

  public void increaseHoldingQuantity(Product product, long quantityToAdd) {
    if (!owns(product)) {
      throw new IllegalArgumentException(
          String.format("Account %s does not own product %s", accountId, product.getId()));
    }

    var holding = holdings.get(product);
    holding.setQuantity(holding.getQuantity() + quantityToAdd);
  }

  public void decreaseHoldingQuantity(Product product, long quantityToSubtract) {
    if (!owns(product)) {
      throw new IllegalArgumentException(
          String.format("Account %s does not own product %s", accountId, product.getId()));
    }

    var holding = holdings.get(product);
    holding.setQuantity(holding.getQuantity() - quantityToSubtract);

    // cleanup if qty reaches 0
    if (holding.getQuantity() == 0) {
      holdings.remove(product);
    }
  }

  public boolean owns(Product product) {
    return holdings.containsKey(product) && holdings.get(product).getQuantity() > 0;
  }

  public long quantityOf(Product product) {
    var h = holdings.get(product);
    return h == null ? 0 : h.getQuantity();
  }
}
