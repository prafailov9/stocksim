package com.ntros;

import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Client;
import com.ntros.simulation.model.Money;
import com.ntros.simulation.model.Product;
import java.math.BigDecimal;
import java.util.List;

public class MarketUtils {

  public static final long MIN_ALLOWED_CENTS = 300;
  public static final long MIN_PRODUCT_PRICE = 1L;
  public static final long PRICE_SENSITIVITY_CENTS = 2; // max-ish 2% move per window

  public static final int MIN_PRODUCT_SEEDING_BOUND = 2;
  public static final int MAX_PRODUCT_SEEDING_BOUND = 5;
  public static final long MAX_PRICE_MOVE_PCT_CENTS = 10; // safety cap: 10%

  private static final double MIN_BUFFER = 1.00;

  public static List<Client> filterOutLowBuyingPower(BigDecimal min, List<Client> clients) {
    List<Client> filtered =
        clients.stream()
            .filter(x -> min.compareTo(Money.bucks(x.getAccount().getAvailableBalance(), 2)) > 0)
            .toList();
    if (!filtered.isEmpty()) {
      clients.removeIf(
          x -> min.compareTo(Money.bucks(x.getAccount().getAvailableBalance(), 2)) > 0);
    }
    return filtered;
  }

  public static List<Client> filterOutLowBuyingPower(List<Product> products, List<Client> clients) {
    return filterOutLowBuyingPower(getMinProductPrice(products), clients);
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

  public static double getTotalBalanceForAllClients(List<Client> clients) {
    double sum = 0.00;
    List<Account> accounts = clients.stream().map(Client::getAccount).toList();
    for (var acc : accounts) {
      sum += acc.getAvailableBalance();
    }
    return sum;
  }

  public static double getAverageBuyingPower(List<Client> clients) {
    return getTotalBalanceForAllClients(clients) / clients.size();
  }
}
