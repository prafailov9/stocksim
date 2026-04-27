package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Portfolio {

  private final int id = IdSequencer.nextPortfolioId();
  private final String name;
  private final List<Product> products;
  private long totalValue = 0;
  private final Map<Product, Holding> holdings;

  public Portfolio(String name, List<Product> products) {
    this.name = name;
    if (products == null) {
      throw new IllegalArgumentException("Products cannot be null");
    }
    holdings = new HashMap<>();
    this.products = products;
    for (var p : products) {

      totalValue += p.getPrice();
    }
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<Product> getProducts() {
    return products;
  }

  public double getTotalValue() {
    return totalValue;
  }

  public void addProduct(Product product) {
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null");
    }
    products.add(product);
    totalValue += product.getPrice();
  }

  public void addHolding(Product product, long quantity) {
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null");
    }
    if (quantity == 0) {
      throw new IllegalArgumentException("Product quantity cannot be 0");
    }

    var holding = new Holding(product.getId(), quantity);
    holdings.put(product, holding);
    totalValue += product.getPrice() * quantity;
  }

  public void removeProduct(Product product) {
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null");
    }
    products.remove(product);
    totalValue -= product.getPrice();
  }
}
