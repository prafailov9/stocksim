package com.ntros;

import com.ntros.simulation.model.Client;
import com.ntros.simulation.model.Market;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.StockMarketSimulation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hello world! */
public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  private static final Random RNG = new Random();

  public static void main(String[] args) {
    Main main = new Main();
    int clientCount = 1_000;
    int productCount = 300;
    int priceBound = 5;
    int maxClientDollars = 10_000;

    Market market = main.buildMarket(productCount, priceBound);
    List<Client> clients = main.buildClients(clientCount, maxClientDollars);
    main.seedPortfolios(clients, new ArrayList<>(market.getAvailableProducts()));
    System.out.println("Built marker and clients.");

    double avg = MarketUtils.getAverageBuyingPower(clients);

    System.out.printf("Average account balance: %s\n", avg);
    StockMarketSimulation simulation = new StockMarketSimulation(market, clients);
    int runtimeMs = 14_000;
    // run
    simulation.run();
    try {
      Thread.sleep(runtimeMs);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // stop
    simulation.stop();
  }

  private Market buildMarket(int products, int priceBound) {
    Market market = new Market("BIG_CHIEFIN");

    for (int i = 1; i <= products; i++) {
      long price = generateProductPriceCents(priceBound);
      market.addProduct(new Product(generateProductCode(), price));
    }

    return market;
  }

  private List<Client> buildClients(int clients, int maxClientDollars) {
    List<Client> list = new ArrayList<>();

    for (int i = 1; i <= clients; i++) {
      list.add(new Client(generateBuyingPowerCents(maxClientDollars)));
    }

    return list;
  }

  private void seedPortfolios(List<Client> clients, List<Product> products) {
    for (var client : clients) {
      // select random amount of unique products for each client
      int rngCount = RNG.nextInt(1, 12);
      HashSet<Product> selectedProducts = new HashSet<>();
      while (selectedProducts.size() < rngCount) {
        int prodIdx = RNG.nextInt(products.size());
        selectedProducts.add(products.get(prodIdx));
      }
      selectedProducts.forEach(p -> client.getAccount().addToPortfolio(p));
    }
  }

  private String generateProductCode() {
    char c1 = (char) RNG.nextInt(97, 122);
    char c2 = (char) RNG.nextInt(97, 122);
    char c3 = (char) RNG.nextInt(97, 122);
    String s = "" + c1 + c2 + c3;
    return s.toUpperCase();
  }

  private long generateProductPriceCents(int maxDollars) {
    return RNG.nextLong(1, maxDollars * 100L + 1);
  }

  // 12% of the time, the initial money for the client will be between 1 and a ceiling between 100
  // and 300
  private long generateBuyingPowerCents(int maxClientDollars) {
    boolean brokieChance = RNG.nextFloat() < 0.12f;

    long minCents = 100L;

    long maxCents =
        brokieChance
            ? RNG.nextLong(100L * 100, 300L * 100 + 1)
            : (long) maxClientDollars * maxClientDollars;

    return RNG.nextLong(minCents, maxCents + 1);
  }
}
