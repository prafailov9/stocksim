package com.ntros.simulation.model;

import com.ntros.simulation.IdSequencer;
import java.util.Objects;

public class Product {

  private final int id;

  private final String code;
  private long price = 0;

  public Product(String code) {
    this.id = IdSequencer.nextProductId();
    this.code = code;
  }

  public Product(String code, long price) {
    this(code);
    this.price = price;
  }

  public int getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public long getPrice() {
    return price;
  }

  public void setPrice(long price) {
    this.price = price;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Product product)) return false;
    return id == product.id && Objects.equals(code, product.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, code);
  }

  @Override
  public String toString() {
    return "Product{"
        + "id="
        + id
        + ", code='"
        + code
        + '\''
        + ", price="
        + Money.bucks(price)
        + '}';
  }
}
