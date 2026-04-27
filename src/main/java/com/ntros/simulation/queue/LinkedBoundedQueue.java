package com.ntros.simulation.queue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedBoundedQueue<E> implements Queue<E> {

  private Node<E> head;
  private Node<E> tail;

  private final int capacity;
  private final AtomicInteger size = new AtomicInteger(0);

  private final ReentrantLock putLock = new ReentrantLock();
  private final ReentrantLock takeLock = new ReentrantLock();

  private final Condition notFull;
  private final Condition notEmpty;

  public LinkedBoundedQueue(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException();
    }

    this.capacity = capacity;

    // Sentinel node. Makes sure producers and consumers never operate on the same node concurrently.
    head = tail = new Node<>(null); // dummy sentinel
    notFull = putLock.newCondition();
    notEmpty = takeLock.newCondition();
  }

  /**
   *
   */
  @Override
  public void put(E item) throws InterruptedException {
    if (item == null) {
      throw new NullPointerException();
    }
    int count; // snapshot of size before put to check state transitions
    putLock.lockInterruptibly();
    try {
      // while producer at capacity, wait
      while (size.get() == capacity) {
        notFull.await();
      }

      // enqueue
      Node<E> node = new Node<>(item);
      tail.next = node;
      tail = node;
      count = size.getAndIncrement();

      // if the current put() has not yet reached the capacity -> awake any sleeping producers
      if (count + 1 < capacity) {
        notFull.signal();
      }
    } finally {
      putLock.unlock();
    }

    // if the queue size transitions from 0 to 1 -> awake any sleeping consumers
    if (count == 0) {
      signalNotEmpty();
    }
  }

  @Override
  public E take() throws InterruptedException {
    E item;
    int count;

    takeLock.lockInterruptibly();
    try {
      while (this.size.get() == 0) {
        notEmpty.await();
      }
      // head is a sentinel node: empty. First real node is head.next
      Node<E> sentinel = head;
      Node<E> firstNode = sentinel.next;
      sentinel.next = sentinel; // move the sentinel forward
      head = firstNode;
      item = firstNode.item;
      firstNode.item = null; // clear new sentinel
      count = this.size.getAndDecrement();

      // if there is still items in the queue, awake any sleeping consumers
      if (count > 1) {
        notEmpty.signal();
      }
    } finally {
      takeLock.unlock();
    }

    // if queue size transitioned from max capacity to max - 1, awake any sleeping producers
    // call under the producer lock
    if (count == capacity) {
      signalNotFull();
    }

    return item;
  }

  @Override
  public E getLast() {
    if (size.get() == 0) {
      return null;
    }

    putLock.lock();
    try {
      return size.get() > 0 ? tail.item : null;
    } finally {
      putLock.unlock();
    }
  }

  @Override
  public E getFirst() {
    if (size.get() == 0) {
      return null;
    }

    takeLock.lock();
    try {
      return (size.get() > 0) ? head.next.item : null;
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public boolean isEmpty() {
    return size.get() == 0;
  }

  @Override
  public int size() {
    return size.get();
  }

  // awake consumers
  private void signalNotEmpty() {
    takeLock.lock();
    try {
      notEmpty.signal();
    } finally {
      takeLock.unlock();
    }
  }

  // awake producers
  private void signalNotFull() {
    putLock.lock();
    try {
      notFull.signal();
    } finally {
      putLock.unlock();
    }
  }

  private static final class Node<E> {

    E item;
    Node<E> next;

    Node(E item) {
      this.item = item;
    }
  }
}
