package com.ntros;

import com.ntros.simulation.model.Trader;
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
  private static final int RUNTIME_MS = 32_000;

  public static void main(String[] args) {
    int clientCount = 5_000;
    int productCount = 18_000;
    int priceBound = 100; // dollars

    Market market = Seeder.seedMarket(productCount, priceBound);
    List<Trader> traders = Seeder.seedTraders(clientCount);
    Seeder.seedPortfolios(traders, new ArrayList<>(market.getAvailableProducts()));

    log.info("Built marker and clients.");
    log.info("Average account balance: {}", MarketUtils.getAverageBuyingPower(traders));

    // start sim
    StockMarketSimulation simulation = new StockMarketSimulation(market, traders);
    log.info("Starting sim...");
    simulation.run();
    try {
      Thread.sleep(RUNTIME_MS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // stop
    simulation.stop();
    log.info("Sim stopped");
  }
}
