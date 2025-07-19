package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.Rectangle;
import com.pseuco.cp25.simulation.common.Context;
import com.pseuco.cp25.simulation.common.Person;

import java.util.List;

/**
 * The `PaddingBuffer` class provides a thread-safe buffer for storing and retrieving
 * a list of `Person` objects. It uses a producer-consumer pattern with synchronized
 * methods to ensure proper coordination between threads. Additionally, it stores
 * a `Rectangle` object representing the area associated with the buffer.
 */
public class PaddingBuffer {
    private final Rectangle area;
    private boolean hasSomethingToRead;
    private List<Person> population;

    /**
     * Constructs a new empty `PaddingBuffer`.
     */
    public PaddingBuffer(Rectangle area) {
        hasSomethingToRead = false;
        this.area = area;
    }

    /**
     * Reads the population data from the buffer.
     * This method blocks if the buffer is empty until data is written to it.
     *
     * @param context The `Context` object used for cloning `Person` objects.
     * @return The list of `Person` objects stored in the buffer.
     * @throws IllegalStateException if the thread is interrupted while waiting.
     */
    public synchronized List<Person> read(Context context) {
        while (!hasSomethingToRead) try {
            this.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread was interrupted unexpectedly during buffer write", e);
        }

        hasSomethingToRead = false;
        this.notifyAll();

        return population.stream().map(person -> person.clone(context)).toList();
    }

    /**
     * Writes population data to the buffer.
     * This method blocks if the buffer already contains data until it is read.
     *
     * @param population The list of `Person` objects to store in the buffer.
     * @throws IllegalStateException if the thread is interrupted while waiting.
     */
    public synchronized void write(List<Person> population) {
        while (hasSomethingToRead) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Thread was interrupted unexpectedly during buffer write", e);
            }
        }

        this.population = population.stream().map(person -> person.clone(null)).toList();

        hasSomethingToRead = true;
        this.notifyAll();
    }

    /**
     * Retrieves the area associated with the padding.
     *
     * @return The `Rectangle` object representing the area of the padding.
     */
    public synchronized Rectangle getArea() {
        return area;
    }
}
