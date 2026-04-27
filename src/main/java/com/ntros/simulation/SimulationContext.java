package com.ntros.simulation;

import com.ntros.simulation.model.Order;
import com.ntros.simulation.model.PriceFlow;
import com.ntros.simulation.model.Product;
import com.ntros.simulation.model.Trader;
import com.ntros.simulation.queue.BoundedMinHeap;
import com.ntros.simulation.queue.LinkedBoundedQueue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public record SimulationContext(
    List<Trader> traders,
    List<Product> availableProducts,
    Map<Integer, PriceFlow> priceFlows,
    List<Map<Integer, PriceFlow>> priceFlowPartitions,
    List<ReentrantLock> clientLocks,
    List<Object> pricingLocks,
    LinkedBoundedQueue<Order> generatedOrders,
    LinkedBoundedQueue<Order> placements,
    BoundedMinHeap<PriceFlow> topMovers,
    AtomicLong processedCount) {}
