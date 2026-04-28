package com.ntros.simulation.model;

/**
 *
 *
 * <pre>
 *    NOISE          60%   Random decisions, baseline market volume
 *    MOMENTUM       30%   Follows price trends, amplifies moves
 *    LONG_TERM      10%   Buys regularly, rarely sells, holds through dips
 * </pre>
 */
public enum TraderType {
  /**
   *
   *
   * <pre>
   * Randomly picks BUY or SELL, random product, random quantity within affordable range. Provides baseline liquidity and volume. No signal dependency.
   * </pre>
   */
  NOISE,

  /**
   *
   *
   * <pre>
   *     Observes price trend direction per product before deciding:
   *     - **On BUY**: selects from products with positive recent delta (trending up). Willing to buy even at higher prices if trend is strong. Larger quantity when trend is stronger.
   *     - **On SELL**: preferentially exits positions in products with negative recent delta (trending down). More aggressive — sells larger fraction of holding than noise traders.
   *     - **Effect**: amplifies existing trends. Rising stocks rise faster, falling stocks fall faster. Creates the momentum effect observed in real markets.
   *     </pre>
   */
  MOMENTUM,

  /**
   *
   *
   * <pre>
   *   Ignores short-term price direction:
   * - **On BUY**: picks products from a personal watchlist or randomly, but always buys a small fixed quantity regardless of recent trend. Buys more aggressively when price has dipped below their average cost.
   * - **On SELL**: very reluctant. Only sells if unrealized loss exceeds a personal panic threshold (e.g. -40% from avg cost). Otherwise holds indefinitely.
   * - **Effect**: provides stabilizing buy pressure during downturns, reduces volatility compared to a market of only noise/momentum traders.
   * </pre>
   */
  LONG_TERM
}
