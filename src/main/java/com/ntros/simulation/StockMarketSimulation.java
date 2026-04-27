package com.ntros.simulation;

import static com.ntros.MarketUtils.MAX_PRICE_MOVE_PCT_CENTS;
import static com.ntros.MarketUtils.MAX_PRODUCT_SEEDING_BOUND;
import static com.ntros.MarketUtils.MIN_ALLOWED_CENTS;
import static com.ntros.MarketUtils.MIN_PRODUCT_PRICE;
import static com.ntros.MarketUtils.MIN_PRODUCT_SEEDING_BOUND;
import static com.ntros.MarketUtils.PRICE_SENSITIVITY_CENTS;
import static com.ntros.simulation.model.Side.BUY;
import static com.ntros.simulation.model.Side.SELL;

import com.ntros.simulation.model.Account;
import com.ntros.simulation.model.Client;
import com.ntros.simulation.model.Market;
import com.ntros.simulation.model.Money;
import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.queue.BoundedMinHeap;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockMarketSimulation {
  private static final Logger log = LoggerFactory.getLogger(StockMarketSimulation.class);

  private static final Random RNG = new Random();

  private static final int MAX_ALLOWED_ORDERS = 10_000_000;
  private static final int MAX_TOP_MOVERS = 10;
  private static final int PRICER_TIMESTEP_MS = 1000;
  private static final int TICKER_TIMESTEP_MS = 2000;

  private static final long BALANCE_FLOOR_CENTS = 5_000_000L;
  private static final long BALANCE_INJECTION_CENTS = 10_000_000L;
  private static final int BALANCE_CHECK_TIMESTEP_MS = 1000;

  private static final int SEEDERS = 3;
  private static final int PLACERS = 3;
  private static final int SETTLERS = 3;
  private static final int PRICERS = 3;

  // data
  private final Market market;
  private final List<Client> clients;
  // TODO: partition products equally between pricers
  private final List<Product> availableProducts;
  private final List<Map<Integer, PriceFlow>> priceFlowPartitions = new ArrayList<>();

  private final Map<Integer, PriceFlow> priceFlows = new HashMap<>();

  // orders
  private final LinkedBoundedQueue<Order> seeded = new LinkedBoundedQueue<>(MAX_ALLOWED_ORDERS);
  private final LinkedBoundedQueue<Order> placements = new LinkedBoundedQueue<>(MAX_ALLOWED_ORDERS);

  private final BoundedMinHeap<PriceFlow> topMovers =
      new BoundedMinHeap<>(
          MAX_TOP_MOVERS, Comparator.comparingLong(flow -> Math.abs(flow.getDelta())));

  // control
  private final Thread[] seeders;
  private final Thread[] placers;
  private final Thread[] settlers;
  private final Thread[] pricers;
  private final Thread ticker;
  private final Thread economist;
  private volatile boolean running = true;
  private volatile boolean ticking = true;

  private boolean canStore = false;
  private final AtomicLong settledCount = new AtomicLong(0);

  // locks
  // TODO: move to lock stripping
  private final List<ReentrantLock> clientLocks = new ArrayList<>();
  private final List<Object> pricingLocks = new ArrayList<>();

  public StockMarketSimulation(Market market, List<Client> clients) {
    this.market = market;
    this.clients = clients;
    this.availableProducts = new ArrayList<>(market.getAvailableProducts());

    // init locks
    clients.forEach(x -> clientLocks.add(new ReentrantLock()));
    availableProducts.forEach(p -> pricingLocks.add(new Object()));

    // init price flows
    for (var p : availableProducts) {
      priceFlows.put(p.getId(), new PriceFlow(p.getId(), p.getCode(), p.getPrice()));
    }

    // partition products between pricers
    for (int i = 0; i < PRICERS; i++) {
      priceFlowPartitions.add(new HashMap<>());
    }

    int x = 0;
    for (var entry : priceFlows.entrySet()) {
      priceFlowPartitions.get(x % PRICERS).put(entry.getKey(), entry.getValue());
      x++;
    }

    // init threads
    seeders = new Thread[SEEDERS];
    for (int i = 0; i < SEEDERS; i++) {
      seeders[i] = new Thread(this::seedOrder, "t-seeder-" + i);
    }

    placers = new Thread[PLACERS];
    for (int i = 0; i < PLACERS; i++) {
      placers[i] = new Thread(this::placeOrder, "t-placer-" + i);
    }

    settlers = new Thread[SETTLERS];
    for (int i = 0; i < SETTLERS; i++) {
      settlers[i] = new Thread(this::settleOrder, "t-executor-" + i);
    }

    pricers = new Thread[PRICERS];
    for (int i = 0; i < PRICERS; i++) {
      var partition = priceFlowPartitions.get(i);
      pricers[i] = new Thread(() -> updatePrices(partition), "t-pricer-" + i);
    }

    ticker = new Thread(this::displayUpdates, "t-ticker-0");
    economist = new Thread(this::manageEconomy, "t-economist-0");
  }

  public void run() {
    for (var t : seeders) {
      t.start();
    }
    for (var t : placers) {
      t.start();
    }
    for (var t : settlers) {
      t.start();
    }
    for (var t : pricers) {
      t.start();
    }
    ticker.start();
    economist.start();
  }

  public void stop() {
    var poison = new Order(null, null);

    // stop producers from adding new orders
    running = false;
    // wait for seeders to finish
    for (var t : seeders) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    // send poison pill to placers
    for (int i = 0; i < PLACERS; i++) {
      try {
        seeded.put(poison);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // wait for still-active producers to finish
    for (var t : placers) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // once producer joining is done, nothing writes to the queue anymore: send poison pill
    for (int i = 0; i < SETTLERS; i++) {
      try {
        placements.put(poison);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // wait for still-consumers to drain the queue and exit
    for (var t : settlers) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // interrupt all then wait for them to finish.
    // interrupting as a separate pass ensures all pricers get the interrupt signal at roughly the
    // same time and can finish their current pricing cycles in parallel.
    for (var t : pricers) {
      t.interrupt();
    }
    for (var t : pricers) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    economist.interrupt();
    try {
      economist.join();
    } catch (InterruptedException e) {
      log.info("economist cucked");
      Thread.currentThread().interrupt();
    }

    ticking = false;
    ticker.interrupt();
    try {
      ticker.join();
    } catch (InterruptedException e) {
      log.info("ticker cucked");
      Thread.currentThread().interrupt();
    }
  }

  // store everything
  public void save() {
    if (canStore) {
      // TODO: store
    }
  }

  /** Seed orders for placement. Seeded orders are validated at placement stage */
  private void seedOrder() {
    while (running) {
      // get market products as list from set
      List<Product> products = availableProducts;

      // init order and add to seeded store
      var client = clients.get(RNG.nextInt(clients.size()));
      var side = RNG.nextFloat() < 0.50f ? BUY : SELL;
      int productsToBuy = 1;
      //          productReservationChance(0.99f, MIN_PRODUCT_SEEDING_BOUND,
      // MAX_PRODUCT_SEEDING_BOUND);
      Set<Product> validatedProducts = new HashSet<>();
      // fix side bias by seeding sell orders from client's portfolio
      if (side == BUY) {
        // product creation chance: x% of the time, an order with only 1 product will be generated

        // generate random products
        while (validatedProducts.size() < productsToBuy) {
          validatedProducts.add(products.get(RNG.nextInt(products.size())));
        }
      } else { // SELLS
        List<Product> owned;
        ReentrantLock clientLock = clientLocks.get(client.getId() - 1);
        clientLock.lock();
        try {
          owned = new ArrayList<>(client.getAccount().getPortfolio().getProducts());
        } finally {
          clientLock.unlock();
        }
        if (owned.isEmpty()) {
          continue;
        } else {
          // add only one sell order per cycle
          validatedProducts.add(owned.get(RNG.nextInt(owned.size())));
        }
      }

      Order order = new Order(client, side);
      order.addAllProducts(new ArrayList<>(validatedProducts));
      try {
        seeded.put(order);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private int productReservationChance(float chance, int min, int max) {
    return RNG.nextFloat() >= chance ? RNG.nextInt(min, max) : 1;
  }

  /**
   * 2nd Stage: Order validation and placement
   *
   * <pre>
   * Reads from seeded orders queue and tries to place an order on each pass.
   * On successful placement validation -> enqueues the order for execution stage.
   * </pre>
   */
  private void placeOrder() {
    while (true) {
      Order order;
      try {
        order = seeded.take();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
      if (order == null) {
        continue;
      }
      // poison pill check, any placer receiving a null client will exit before placing a new order
      if (order.getClient() == null) {
        break;
      }
      // get market products as list from set
      List<Product> orderedProducts = order.getProducts();
      var client = order.getClient();

      // get prices, total
      Map<Integer, Long> prices = new HashMap<>();
      long totalPrice = 0L;
      for (var product : orderedProducts) {
        synchronized (pricingLocks.get(product.getId() - 1)) {
          // TODO: add fee
          prices.put(product.getId(), product.getPrice());
          totalPrice += product.getPrice();
        }
      }

      var side = order.getSide();
      int clientIdx = order.getClient().getId() - 1;
      var validatedOrder = new Order(client, side);
      // for [BUY] orders, check if client has enough money
      // for [SELL] orders, if client owns the product
      ReentrantLock clientLock = clientLocks.get(clientIdx);
      clientLock.lock();
      try {
        if (side.equals(BUY)) {
          long availableBalance = client.getAccount().getAvailableBalance();
          // try full buy
          if (availableBalance > totalPrice && availableBalance - totalPrice >= MIN_ALLOWED_CENTS) {
            client.getAccount().decreaseAvailableBalance(totalPrice);
            client.getAccount().increaseReservedBalance(totalPrice);
            validatedOrder.addAllProducts(orderedProducts);
          } else {
            // try partial buy
            long partialPrice = 0L;
            for (var ordered : orderedProducts) {
              long nextPrice = partialPrice + ordered.getPrice();
              if (nextPrice <= availableBalance
                  && availableBalance - nextPrice >= MIN_ALLOWED_CENTS) {
                partialPrice = nextPrice;
                validatedOrder.addProduct(ordered);
              }
            }
            // partial buy failed - skip
            if (partialPrice == 0) {
              //              log.info(
              //                  "!!!BROKIE ALERT!!! Client {} is too broke. TotalBuyPrice: {},
              // minBuyingPowerAllowed: {}, clientAvailableBalance: {}",
              //                  client.getId(),
              //                  Money.bucks(totalPrice),
              //                  Money.bucks(MIN_ALLOWED_CENTS),
              //                  Money.bucks(availableBalance));
              continue;
            }
            client.getAccount().decreaseAvailableBalance(partialPrice);
            client.getAccount().increaseReservedBalance(partialPrice);
          }
        } else {
          // check ordered against owned, only allow once who match
          List<Product> ownedProducts = client.getAccount().getPortfolio().getProducts();
          for (var ordered : orderedProducts) {
            if (ownedProducts.contains(ordered)) {
              client
                  .getAccount()
                  .removeFromPortfolio(
                      ordered); // reserve by removing, same as balance for BUY order
              validatedOrder.addProduct(ordered);
            }
          }
          // skip placement if no ordered products and owned
          if (validatedOrder.getProducts().isEmpty()) {
            continue;
          }
        }
      } finally {
        clientLock.unlock();
      }
      // place the order
      try {
        placements.put(validatedOrder);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void settleOrder() {
    // poison pill handles control
    while (true) {
      Order order;
      try {
        order = placements.take();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
      // poison pill check, always check first
      if (order.getClient() == null) {
        break;
      }

      if (order.getProducts().isEmpty()) {
        continue;
      }
      // structure ensures indices are always clientId - 1
      int clientIdx = order.getClient().getId() - 1;
      // modify account balance
      ReentrantLock clientLock = clientLocks.get(clientIdx);
      clientLock.lock();
      try {
        Account account = order.getClient().getAccount();
        if (order.side().equals(BUY)) {
          account.decreaseReservedBalance(order.getOrderPrice());
          order.getProducts().forEach(account::addToPortfolio);
        } else {
          // portfolio already updated at placement
          account.increaseAvailableBalance(order.getOrderPrice());
        }
      } finally {
        clientLock.unlock();
      }
      settledCount.incrementAndGet();

      // adjust price flow for product
      for (var p : order.getProducts()) {
        var pricingLock = pricingLocks.get(p.getId() - 1);
        synchronized (pricingLock) {
          var priceFlow = priceFlows.get(p.getId());
          if (priceFlow == null) {
            throw new RuntimeException(
                String.format("No priceFlow exists for productId: %s", p.getId()));
          }
          if (order.getSide().equals(BUY)) {
            priceFlow.increaseBuys();
          } else {
            priceFlow.increaseSells();
          }
        }
      }
    }
  }

  private void updatePrices(Map<Integer, PriceFlow> partition) {
    while (running) {
      /// arbitrary time window for price fluctuation.
      try {
        Thread.sleep(PRICER_TIMESTEP_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt(); // restore interrupt flag
        break;
      }

      for (var entry : partition.entrySet()) {
        int productIdx = entry.getKey() - 1;
        var flow = entry.getValue();

        long buys;
        long sells;

        synchronized (pricingLocks.get(productIdx)) {
          buys = flow.getBuys();
          sells = flow.getSells();
        }
        long totalVolume = buys + sells;
        long distance = buys - sells;

        if (totalVolume == 0) {
          continue;
        }

        synchronized (pricingLocks.get(productIdx)) {
          var product = availableProducts.get(productIdx);

          double distanceRatio = (double) distance / totalVolume;
          double movePct = distanceRatio * PRICE_SENSITIVITY_CENTS;
          movePct =
              Math.max(-MAX_PRICE_MOVE_PCT_CENTS, Math.min(MAX_PRICE_MOVE_PCT_CENTS, movePct));

          flow.resetBuys();
          flow.resetSells();
          long oldPrice = product.getPrice();
          long delta = Math.round(oldPrice * movePct);
          long newPrice = Math.max(oldPrice + delta, MIN_PRODUCT_PRICE);
          product.setPrice(newPrice);
          flow.setDelta(delta);
          flow.setCurrentPrice(newPrice);
          topMovers.offer(flow.snapshot(buys, sells)); // pass the values read before reset
        }
      }
    }
  }

  private void displayUpdates() {
    while (ticking) {
      try {
        Thread.sleep(TICKER_TIMESTEP_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }

      // drain top movers
      List<PriceFlow> movers = new ArrayList<>();
      int size = topMovers.size();
      for (int i = 0; i < size; i++) {
        movers.add(topMovers.poll());
      }
      if (movers.isEmpty()) {
        continue;
      }

      // sort descending by |delta| for display
      movers.sort((a, b) -> Long.compare(Math.abs(b.getDelta()), Math.abs(a.getDelta())));
      // deduplicate by product code, keep first (highest delta) occurrence
      Set<String> seen = new HashSet<>();
      List<PriceFlow> unique = new ArrayList<>();
      for (var mover : movers) {
        if (seen.add(mover.getCode())) {
          unique.add(mover);
        }
      }
      // clear terminal
      System.out.print("\033[H\033[2J");
      System.out.flush();

      // header
      String timestamp =
          java.time.LocalTime.now()
              .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
      System.out.printf(
          "  %-6s  %-12s  %-12s  %-10s  %-6s  %-6s  %s%n",
          "CODE", "PRICE", "DELTA", "MOVE %", "BUYS", "SELLS", "LAST UPDATE: " + timestamp);
      System.out.println("  " + "─".repeat(72));

      // rows
      for (var mover : unique) {
        long delta = mover.getDelta();
        long price = mover.getCurrentPrice();

        String arrow = delta > 0 ? "↑" : delta < 0 ? "↓" : "─";
        String ansi = delta > 0 ? "\033[32m" : delta < 0 ? "\033[31m" : "\033[37m";
        String reset = "\033[0m";

        // percentage move relative to price before delta was applied
        long prevPrice = price - delta;
        double movePct = prevPrice != 0 ? (delta * 100.0) / prevPrice : 0.0;

        System.out.printf(
            ansi + "  %-6s  %-12s  %-12s  %-10s  %-6d  %-6d  %s" + reset + "%n",
            mover.getCode(),
            Money.bucks(price),
            Money.bucks(Math.abs(delta)),
            String.format("%s%.2f%%", delta >= 0 ? "+" : "-", Math.abs(movePct)),
            mover.getBuys(),
            mover.getSells(),
            arrow);
      }

      // footer
      System.out.printf("%n  Total settled orders: %d%n", settledCount.get());
    }
  }

  private void manageEconomy() {
    while (running) {
      try {
        Thread.sleep(BALANCE_CHECK_TIMESTEP_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
      for (int i = 0; i < clients.size(); i++) {
        ReentrantLock lock = clientLocks.get(i);
        // skip if settler is using this client right now
        if (!lock.tryLock()) {
          continue;
        }
        try {
          Account account = clients.get(i).getAccount();
          if (account.getAvailableBalance() < BALANCE_FLOOR_CENTS) {
            account.increaseAvailableBalance(BALANCE_INJECTION_CENTS);
          }
          if (account.getPortfolio().getProducts().isEmpty()) {
            int count = RNG.nextInt(1, 6);
            for (int j = 0; j < count; j++) {
              account.addToPortfolio(availableProducts.get(RNG.nextInt(availableProducts.size())));
            }
          }
        } finally {
          lock.unlock();
        }
      }
    }
  }
}
