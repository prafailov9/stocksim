package com.ntros;

import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.model.Money;
import com.ntros.simulation.model.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MarketUtils {

  public static final long MIN_ALLOWED_CENTS = 300;
  public static final long MIN_PRODUCT_PRICE = 1L;
  public static final long PRICE_SENSITIVITY_CENTS = 1; // max-ish 1% move per window

  public static final long MAX_PRICE_MOVE_PCT_CENTS = 3; // safety cap: 3%

  private static final double MIN_BUFFER = 1.00;

  public static List<Trader> filterOutLowBuyingPower(BigDecimal min, List<Trader> traders) {
    List<Trader> filtered =
        traders.stream()
            .filter(x -> min.compareTo(Money.bucks(x.getAccount().getAvailableBalance(), 2)) > 0)
            .toList();
    if (!filtered.isEmpty()) {
      traders.removeIf(
          x -> min.compareTo(Money.bucks(x.getAccount().getAvailableBalance(), 2)) > 0);
    }
    return filtered;
  }

  public static List<Trader> filterOutLowBuyingPower(List<Product> products, List<Trader> traders) {
    return filterOutLowBuyingPower(getMinProductPrice(products), traders);
  }

  public static BigDecimal getMinProductPrice(List<Product> products) {
    BigDecimal minPrice = BigDecimal.valueOf(Double.MAX_VALUE);
    for (var p : products) {
      BigDecimal bigBucks = Money.bucks(p.getPrice(), 2);
      if (minPrice.compareTo(bigBucks) > 0) {
        minPrice = bigBucks;
      }
    }

    return minPrice;
  }

  public static BigDecimal getMinAllowedBuyingPower(List<Product> products) {
    return getMinProductPrice(products).add(BigDecimal.valueOf(MIN_BUFFER));
  }

  public static long getTotalBalanceForAllTraders(List<Trader> traders) {
    return traders.stream()
        .map(Trader::getAccount)
        .mapToLong(Account::getAvailableBalance)
        .sum();
  }

  public static BigDecimal getAverageBuyingPower(List<Trader> traders) {
    long totalCents = getTotalBalanceForAllTraders(traders);

    return BigDecimal.valueOf(totalCents)
        .divide(BigDecimal.valueOf(traders.size() * 100L), 2, RoundingMode.HALF_UP);
  }

}
