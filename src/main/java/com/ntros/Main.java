package com.ntros;

import com.ntros.simulation.model.Trader;
import com.ntros.simulation.model.Market;
import com.ntros.simulation.StockMarketSimulation;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hello world! */
public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);
  private static final int TOTAL_RUNTIME_MS = 64_000; // seconds

  public static void main(String[] args) {
    int traderCount = 50_000;
    int productCount = 8_000;
    int priceBound = 100; // dollars

    Market market = Generator.generateMarket(productCount, priceBound);

    // sets initial trading behavior. "true" -> random order generation, regardless of trader type
    boolean onlyNoise = false;
    List<Trader> traders = Generator.generateTraders(traderCount, onlyNoise);
    Generator.generatePortfolios(traders, new ArrayList<>(market.getAvailableProducts()));

    String avgBalancesString = MarketUtils.getAverageBuyingPowerString(traders);

    log.info("Built market and traders.");
    log.info("Average account balance: {}", avgBalancesString);

    // start sim
    StockMarketSimulation simulation = new StockMarketSimulation(market, traders);
    log.info("Starting sim for {} seconds...", TOTAL_RUNTIME_MS / 1000);
    simulation.run();
    try {
      Thread.sleep(TOTAL_RUNTIME_MS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // stop
    simulation.stop();
    log.info("Sim stopped");
    log.info("Average account balance after sim: {}", MarketUtils.getAverageBuyingPowerString(traders));

  }
}
