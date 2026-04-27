package com.ntros.simulation.queue;

import java.util.Comparator;

public class MaxHeap<E> {

  private final int capacity;

  private final Object[] arr;
  private final Comparator<E> comparator;

  private int size;

  public MaxHeap(int capacity, Comparator<E> comparator) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Heap capacity must be > 0");
    }
    if (comparator == null) {
      throw new IllegalArgumentException("Comparator cannot be null");
    }
    this.capacity = capacity;
    this.comparator = comparator;
    // init structure
    arr = new Object[capacity];
  }

  public boolean offer(E value) {
    // add at end of arr
    if (size == capacity) {
      return false;
    }
    int currentIdx = size;
    arr[currentIdx] = value;
    size++;

    int parentIdx = (currentIdx - 1) / 2;
    // while invalid priority or not at root:
    while (currentIdx > 0 && hasHigherPriority(elementAt(currentIdx), elementAt(parentIdx))) {

      // swap
      Object temp = arr[currentIdx];
      arr[currentIdx] = arr[parentIdx];
      arr[parentIdx] = temp;
      currentIdx = parentIdx;

      // get new parent
      parentIdx = (currentIdx - 1) / 2;
    }
    return true;
  }

  public E peek() {
    if (isEmpty()) {
      return null;
    }
    return elementAt(0);
  }

  public E poll() {
    if (isEmpty()) {
      return null;
    }
    // replace first with last
    // delete old last
    // shrink array
    E rootValue = elementAt(0);
    arr[0] = arr[size - 1];
    arr[size - 1] = null;
    size--;

    int currentIdx = 0;
    // get children
    while (true) {
      int leftIdx = 2 * currentIdx + 1;
      int rightIdx = 2 * currentIdx + 2;
      int priorityIdx;

      if (leftIdx >= size) {
        break;
      }

      priorityIdx = leftIdx;
      if (rightIdx < size && hasHigherPriority(elementAt(rightIdx), elementAt(leftIdx))) {
        priorityIdx = rightIdx;
      }
      if (hasHigherOrEqualPriority(elementAt(currentIdx), elementAt(priorityIdx))) {
        break;
      }
      Object temp = arr[currentIdx];
      arr[currentIdx] = arr[priorityIdx];
      arr[priorityIdx] = temp;

      currentIdx = priorityIdx;
    }
    return rootValue;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int size() {
    return size;
  }

  public boolean isFull() {
    return size == capacity;
  }

  @SuppressWarnings("unchecked")
  private E elementAt(int index) {
    return (E) arr[index];
  }

  private boolean hasHigherPriority(E o1, E o2) {
    return comparator.compare(o1, o2) > 0;
  }

  private boolean hasHigherOrEqualPriority(E o1, E o2) {
    return comparator.compare(o1, o2) >= 0;
  }
}
