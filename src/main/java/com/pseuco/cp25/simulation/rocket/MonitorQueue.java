package com.pseuco.cp25.simulation.rocket;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A simple queue supporting only enqueuing(adding) and dequeuing(removing) elements.
 *
 * @param <T>
 */
public class MonitorQueue<T> {
    private final Queue<T> queue;

    /**
     * Creates an empty queue.
     */
    public MonitorQueue() {
        queue = new ArrayDeque<>();
    }

    /**
     * Enqueues element.
     * @param e Element to queue.
     */
    public synchronized void enqueue(T e) {
        queue.add(e);
        this.notifyAll();
    }

    /**
     * Returns the head of the queue and removes it.
     * @return head of the queue.
     */
    public synchronized T dequeue() {
        while (queue.isEmpty()) try {
            this.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread was interrupted unexpectedly.", e);
        }
        return queue.remove();
    }

}
