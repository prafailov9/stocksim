package com.ntros.simulation.queue;

import java.util.Comparator;

public class BoundedMinHeap<E> {

  private final int capacity;
  private final Object[] arr;
  private final Comparator<E> comparator;

  private int size;

  public BoundedMinHeap(int capacity, Comparator<E> comparator) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Heap capacity must be > 0");
    }
    if (comparator == null) {
      throw new IllegalArgumentException("Comparator cannot be null");
    }

    this.capacity = capacity;
    this.comparator = comparator;
    this.arr = new Object[capacity];
  }

  public synchronized boolean offer(E value) {
    if (value == null) {
      throw new IllegalArgumentException("Heap value cannot be null");
    }

    if (size < capacity) {
      insert(value);
      return true;
    }

    E root = elementAt(0);

    // If new value is not better than the weakest kept value, ignore it.
    if (comparator.compare(value, root) <= 0) {
      return false;
    }

    // Replace weakest kept value with the new stronger value.
    arr[0] = value;
    bubbleDown(0);
    return true;
  }

  public synchronized E peek() {
    if (isEmpty()) {
      return null;
    }

    return elementAt(0);
  }

  public synchronized E poll() {
    if (isEmpty()) {
      return null;
    }

    E rootValue = elementAt(0);

    arr[0] = arr[size - 1];
    arr[size - 1] = null;
    size--;

    if (!isEmpty()) {
      bubbleDown(0);
    }

    return rootValue;
  }

  public synchronized boolean isEmpty() {
    return size == 0;
  }

  public synchronized boolean isFull() {
    return size == capacity;
  }

  public synchronized int size() {
    return size;
  }

  public synchronized int capacity() {
    return capacity;
  }

  public synchronized void clear() {
    for (int i = 0; i < size; i++) {
      arr[i] = null;
    }

    size = 0;
  }

  private void insert(E value) {
    int currentIdx = size;
    arr[currentIdx] = value;
    size++;

    bubbleUp(currentIdx);
  }

  private void bubbleUp(int currentIdx) {
    while (currentIdx > 0) {
      int parentIdx = (currentIdx - 1) / 2;

      if (hasLowerOrEqualPriority(elementAt(parentIdx), elementAt(currentIdx))) {
        break;
      }

      swap(currentIdx, parentIdx);
      currentIdx = parentIdx;
    }
  }

  private void bubbleDown(int currentIdx) {
    while (true) {
      int leftIdx = 2 * currentIdx + 1;
      int rightIdx = 2 * currentIdx + 2;

      if (leftIdx >= size) {
        break;
      }

      int priorityIdx = leftIdx;

      if (rightIdx < size && hasLowerPriority(elementAt(rightIdx), elementAt(leftIdx))) {
        priorityIdx = rightIdx;
      }

      if (hasLowerOrEqualPriority(elementAt(currentIdx), elementAt(priorityIdx))) {
        break;
      }

      swap(currentIdx, priorityIdx);
      currentIdx = priorityIdx;
    }
  }

  private void swap(int firstIdx, int secondIdx) {
    Object temp = arr[firstIdx];
    arr[firstIdx] = arr[secondIdx];
    arr[secondIdx] = temp;
  }

  @SuppressWarnings("unchecked")
  private E elementAt(int index) {
    return (E) arr[index];
  }

  private boolean hasLowerPriority(E first, E second) {
    return comparator.compare(first, second) < 0;
  }

  private boolean hasLowerOrEqualPriority(E first, E second) {
    return comparator.compare(first, second) <= 0;
  }
}
