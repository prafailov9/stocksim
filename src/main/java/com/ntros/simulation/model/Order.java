package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {

  private static final double MIN_ORDER_PRICE = 1.00;

  private final int id;
  private final Client client;
  private final List<Product> products;
  private final Side side;
  private long orderPrice = 0;

  public Order(Client client, Side side) {
    id = IdSequencer.nextOrderId();
    this.client = client;
    products = new ArrayList<>();
    this.side = side;
  }

  public Order(Client client, Product product, Side side) {
    this(client, side);
    if (product == null) {
      throw new IllegalArgumentException("Product cannot be null.");
    }
    addProduct(product);
  }

  public int getId() {
    return id;
  }

  public Client getClient() {
    return client;
  }

  public Side getSide() {
    return side;
  }

  public List<Product> getProducts() {
    return products;
  }

  public void addProduct(Product product) {
    validateProduct(product);
    products.add(product);
    orderPrice += product.getPrice();
  }

  public Side side() {
    return side;
  }

  public long getOrderPrice() {
    return orderPrice;
  }

  public void addAllProducts(List<Product> p) {
    p.forEach(this::validateProduct);
    products.addAll(p);
    p.forEach(x -> orderPrice += x.getPrice());
  }

  public void removeProduct(Product product) {
    validateProduct(product);
    products.remove(product);
    orderPrice -= (long) product.getPrice();
  }

  public void removeAllProducts(List<Product> p) {
    p.forEach(this::validateProduct);
    products.removeAll(p);
    for (var product : p) {
      if (orderPrice - product.getPrice() > MIN_ORDER_PRICE) {
        orderPrice -= product.getPrice();
      } else {
        // log
        System.out.printf("cannot remove product from order list: %s%n", p);
        break;
      }
    }
  }

  @Override
  public String toString() {
    return "Order{"
        + "id="
        + id
        + ", products="
        + products
        + ", orderPrice="
        + Money.bucks(orderPrice)
        + '}';
  }

  void validateProduct(Product product) {
    if (product == null || product.getId() <= 0) {
      throw new IllegalArgumentException(String.format("Invalid product: %s", product));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Order order)) return false;
    return id == order.id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
