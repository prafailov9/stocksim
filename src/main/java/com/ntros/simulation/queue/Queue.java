package com.ntros.simulation.queue;

public interface Queue<E> {

  void put(E item) throws InterruptedException;

  E take() throws InterruptedException;

  E getLast() throws InterruptedException;

  E getFirst() throws InterruptedException;

  boolean isEmpty();

  int size();
}
