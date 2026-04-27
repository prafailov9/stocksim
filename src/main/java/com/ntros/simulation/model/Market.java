package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.HashSet;
import java.util.Set;

public class Market {

  private final int id;
  private final String name;
  private final Set<Product> availableProducts;

  public Market(String name) {
    id = IdSequencer.nextMarketId();
    this.name = name;
    availableProducts = new HashSet<>(); // could read init products from file
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Set<Product> getAvailableProducts() {
    return availableProducts;
  }

  public void addProduct(Product product) {
    availableProducts.add(product);
  }
}
