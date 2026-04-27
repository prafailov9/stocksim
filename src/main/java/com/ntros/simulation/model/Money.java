package com.ntros.simulation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Money {

  private static final int EXPONENT = 2;

  public static long cents(BigDecimal bucks) {
    try {
      BigDecimal normalized = bucks.setScale(EXPONENT, RoundingMode.UNNECESSARY);
      return normalized.movePointRight(EXPONENT).longValueExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(
          "Invalid amount scale for currency exponent=" + EXPONENT + ", amount=" + bucks, ex);
    }
  }

  public static long cents(BigDecimal bucks, int exponent) {
    try {
      BigDecimal normalized = bucks.setScale(exponent, RoundingMode.UNNECESSARY);
      return normalized.movePointRight(exponent).longValueExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(
          "Invalid amount scale for currency exponent=" + exponent + ", amount=" + bucks, ex);
    }
  }

  public static BigDecimal bucks(long cents, int exponent) {
    return BigDecimal.valueOf(cents, exponent);
  }
  public static BigDecimal bucks(long cents) {
    return BigDecimal.valueOf(cents, EXPONENT);
  }
}
