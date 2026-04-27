package com.ntros;

import static com.ntros.InitialWealthTier.AFFLUENT;
import static com.ntros.InitialWealthTier.HIGH_NET_WORTH;
import static com.ntros.InitialWealthTier.REGULAR;
import static com.ntros.InitialWealthTier.SMALL;
import static com.ntros.InitialWealthTier.WHALE;

import com.ntros.simulation.model.Market;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.model.Trader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class Generator {
  private static final Random RNG = new Random();
  private static final long EXPONENT = 100L;

  static Market generateMarket(int products, int priceBound) {
    Market market = new Market("BIG_CHIEFIN");

    for (int i = 1; i <= products; i++) {
      long price = generateProductPriceCents(priceBound);
      market.addProduct(new Product(generateProductCode(), price));
    }

    return market;
  }

  // generates its own buying power
  static List<Trader> generateTraders(int total) {
    List<Trader> traders = new ArrayList<>();

    for (int i = 1; i <= total; i++) {
      // roll wealth dice: brokie - low balance, bourgeoisie - mid balance, bezos - top tier
      float buyPowerChance = RNG.nextFloat();
      var range = getRange(buyPowerChance);
      var startingBalance = RNG.nextLong(range.min, range.max + 1);

      traders.add(new Trader(startingBalance, range.tier));
    }

    return traders;
  }

  static void generatePortfolios(List<Trader> traders, List<Product> products) {
    for (var trader : traders) {
      InitialWealthTier tier = trader.getWealthTier();

      int ownedProductsCount = generateOwnedProductsCount(tier, products.size());

      HashSet<Product> selectedProducts = new HashSet<>();
      while (selectedProducts.size() < ownedProductsCount) {
        int prodIdx = RNG.nextInt(products.size());
        selectedProducts.add(products.get(prodIdx));
      }

      for (Product product : selectedProducts) {
        long quantity = generateQuantity(tier, product);
        if (quantity > 0) {
          trader.getAccount().getPortfolio().addHolding(product, quantity);
        }
      }
    }
  }

  /// helpers
  private static long generateQuantity(InitialWealthTier tier, Product product) {
    long minPositionValue;
    long maxPositionValue;

    switch (tier) {
      case SMALL -> {
        minPositionValue = 50 * EXPONENT;
        maxPositionValue = 2_000 * EXPONENT;
      }
      case REGULAR -> {
        minPositionValue = 500 * EXPONENT;
        maxPositionValue = 25_000 * EXPONENT;
      }
      case AFFLUENT -> {
        minPositionValue = 5_000 * EXPONENT;
        maxPositionValue = 250_000 * EXPONENT;
      }
      case HIGH_NET_WORTH -> {
        minPositionValue = 25_000 * EXPONENT;
        maxPositionValue = 2_500_000 * EXPONENT;
      }
      case WHALE -> {
        minPositionValue = 100_000 * EXPONENT;
        maxPositionValue = 25_000_000 * EXPONENT;
      }
      default -> throw new IllegalStateException("Unexpected tier: " + tier);
    }

    long positionValue = skewedLong(minPositionValue, maxPositionValue);
    long quantity = positionValue / product.getPrice();

    return Math.max(1, quantity);
  }

  private static long skewedLong(long min, long max) {
    double r = RNG.nextDouble();

    // Squaring biases toward smaller values.
    double skewed = r * r;

    return min + (long) ((max - min) * skewed);
  }

  private static int generateOwnedProductsCount(InitialWealthTier tier, int productCount) {
    int count =
        switch (tier) {
          case SMALL -> RNG.nextInt(0, 5); // 0–4
          case REGULAR -> RNG.nextInt(2, 9); // 2–8
          case AFFLUENT -> RNG.nextInt(4, 16); // 4–15
          case HIGH_NET_WORTH -> RNG.nextInt(8, 31); // 8–30
          case WHALE -> RNG.nextInt(15, 81); // 15–80
        };

    return Math.min(count, productCount);
  }

  private static long generateProductPriceCents(int maxDollars) {
    return RNG.nextLong(1, maxDollars * EXPONENT + 1);
  }

  // 3 random chars between A - Z
  private static String generateProductCode() {
    char c1 = (char) RNG.nextInt(97, 122);
    char c2 = (char) RNG.nextInt(97, 122);
    char c3 = (char) RNG.nextInt(97, 122);
    String s = "" + c1 + c2 + c3;
    return s.toUpperCase();
  }

  // range values are in dollars
  // brokie:50%,  $100 - $5000
  // bourg:30%, $5000 - $75 000
  // affluent: 10%, $75000 - $500000
  // High net-worth: 4%, $500000 - $5 000 000
  // bezos: 1%, $5 000 000 +
  private static Range getRange(float chance) {
    if (chance <= 0.5f) {
      return new Range(SMALL, 100 * EXPONENT, 5_000 * EXPONENT);
    }
    if (chance > 0.5f && chance <= 0.8f) {
      return new Range(REGULAR, 5_000 * EXPONENT, 75_000 * EXPONENT);
    }
    if (chance > 0.8f && chance <= 0.9f) {
      return new Range(AFFLUENT, 75_000 * EXPONENT, 500_000 * EXPONENT);
    }
    if (chance > 0.9f && chance <= 0.99f) {
      return new Range(HIGH_NET_WORTH, 500_000 * EXPONENT, 5_000_000 * EXPONENT);
    }
    return new Range(WHALE, 5_000_000 * EXPONENT, 10_000_000 * EXPONENT);
  }

  private static final class Range {
    InitialWealthTier tier;
    long min;
    long max;

    Range(InitialWealthTier tier, long min, long max) {
      this.tier = tier;
      this.min = min;
      this.max = max;
    }
  }
}
