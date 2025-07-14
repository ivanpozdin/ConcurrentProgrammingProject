package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.PersonInfo;
import com.pseuco.cp25.model.Rectangle;

import java.util.List;

/**
 * The `PaddingBuffer` class provides a thread-safe buffer for storing and retrieving
 * a list of `PersonInfo` objects. It uses a producer-consumer pattern with synchronized
 * methods to ensure proper coordination between threads. Additionally, it stores
 * a `Rectangle` object representing the area associated with the buffer.
 */
public class PaddingBuffer {
    private boolean hasSomethingToRead;
    private List<PersonInfo> population;
    private final Rectangle area;

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
     * @return The list of `PersonInfo` objects stored in the buffer.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public synchronized List<PersonInfo> read() throws InterruptedException {
        while (!hasSomethingToRead) this.wait();

        hasSomethingToRead = false;
        this.notifyAll();

        return population;
    }

    /**
     * Writes population data to the buffer.
     * This method blocks if the buffer already contains data until it is read.
     *
     * @param population The list of `PersonInfo` objects to store in the buffer.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public synchronized void write(List<PersonInfo> population) throws InterruptedException {
        while (hasSomethingToRead) this.wait();

        this.population = population;

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
