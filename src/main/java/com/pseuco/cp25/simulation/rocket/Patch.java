package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.*;
import com.pseuco.cp25.simulation.common.Context;
import com.pseuco.cp25.simulation.common.Person;
import com.pseuco.cp25.validator.Validator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a patch, which is a part of the simulation grid.
 * Handles simulation of its part of the population, synchronization with its
 * PaddingBuffers, and communicating output via queue.
 */
public class Patch implements Runnable, Context {
    private final int patchId;
    private final int cycleDuration;
    private final Scenario scenario;
    private final Map<String, Query> queriesInPaddedArea = new HashMap<>();
    private final List<Rectangle> obstaclesInPaddedArea;

    // Patch-specific fields
    private final List<PaddingBuffer> innerPaddings = new ArrayList<>();
    private final List<PaddingBuffer> outerPaddings = new ArrayList<>();
    private final Rectangle patchArea;
    private final Rectangle paddedArea;

    // Statistics-related fields
    private final List<List<PersonInfoWithId>> trace = new ArrayList<>();
    private final MonitorQueue<OutputEntry> outputQueue;
    private final Validator validator;
    private List<Person> combinedPopulation;
    private List<Person> patchPopulation;

    /**
     * Constructs a new Patch instance.
     *
     * @param patchPopulation The initial population of the patch.
     * @param patchArea       The area of the patch.
     * @param paddedArea      The padded area around the patch.
     * @param cycleDuration   The duration of a simulation cycle.
     * @param scenario        The simulation scenario.
     * @param patchId         The unique identifier of the patch.
     * @param validator       The validator for automatic testing.
     */
    public Patch(
            List<Person> patchPopulation,
            Rectangle patchArea,
            Rectangle paddedArea,
            int cycleDuration,
            Scenario scenario,
            int patchId,
            Validator validator,
            MonitorQueue<OutputEntry> outputQueue
    ) {
        this.patchPopulation = patchPopulation.stream().map(person -> person.clone(this)).toList();
        this.patchArea = patchArea;
        this.paddedArea = paddedArea;
        this.cycleDuration = cycleDuration;
        this.scenario = scenario;
        this.patchId = patchId;
        this.validator = validator;
        this.outputQueue = outputQueue;

        obstaclesInPaddedArea =
                scenario.getObstacles().stream().filter(obstacle -> obstacle.overlaps(paddedArea)).toList();

        scenario.getQueries().forEach((key, query) -> {
            if (query.getArea().overlaps(paddedArea)) {
                queriesInPaddedArea.put(key, query);
            }
        });

        this.extendOutput(0);
        this.combinedPopulation = new ArrayList<>();
    }

    /**
     * Adds inner padding. Use only for initialization.
     *
     * @param paddingBuffer representing inner padding.
     */
    public void addInnerPadding(PaddingBuffer paddingBuffer) {
        innerPaddings.add(paddingBuffer);
    }

    /**
     * Adds outer padding. Use only for initialization.
     *
     * @param paddingBuffer representing outer padding.
     */
    public void addOuterPadding(PaddingBuffer paddingBuffer) {
        outerPaddings.add(paddingBuffer);
    }

    /**
     * Getter for patch's area.
     *
     * @return patch's area
     */
    public Rectangle getPatchArea() {
        return patchArea;
    }

    /**
     * Getter for patch's padded area.
     *
     * @return patch's padded area
     */
    public Rectangle getPaddedArea() {
        return paddedArea;
    }

    /**
     * Returns the simulation area including padding around the patch.
     *
     * @return simulation area (for `Person`'s context).
     */
    @Override
    public Rectangle getGrid() {
        return paddedArea;
    }


    @Override
    public List<Rectangle> getObstacles() {
        return obstaclesInPaddedArea;
    }

    /**
     * Returns all persons to be considered when simulating the person.
     * In this case, also people in the padding around the patch.
     *
     * @return All persons to be considered when simulating the person.
     */
    @Override
    public List<Person> getPopulation() {
        return combinedPopulation;
    }


    /**
     * Does simulation on the patch.
     * Does synchronization with `PaddingBuffer`'s after each cycleDuration ticks.
     */
    @Override
    public void run() {
        for (int tickNumber = 0; tickNumber < this.scenario.getTicks(); tickNumber++) {
            if (tickNumber % cycleDuration == 0) {
                doSynchronization();
            }
            if (Thread.interrupted()) {
                throw new IllegalStateException("Thread was interrupted unexpectedly.");
            }
            validator.onPatchTick(tickNumber, patchId);
            this.tick(tickNumber);
        }
    }

    /**
     * Returns the full trace collected during simulation.
     * To be called after simulation is finished.
     *
     * @return The full trace collected during simulation.
     */
    public List<List<PersonInfoWithId>> getTrace() {
        return trace;
    }

    private void tick(int tickNumber) {
        for (Person person : combinedPopulation) {
            if (Thread.interrupted()) {
                throw new IllegalStateException("Thread was interrupted unexpectedly.");
            }
            validator.onPersonTick(tickNumber, patchId, person.getId());
            person.tick();
        }
        // bust the ghosts of all persons
        this.combinedPopulation.forEach(Person::bustGhost);

        // now compute how the infection spreads between the combinedPopulation
        for (int i = 0; i < this.combinedPopulation.size(); i++) {
            for (int j = i + 1; j < this.combinedPopulation.size(); j++) {
                final Person iPerson = this.combinedPopulation.get(i);
                final Person jPerson = this.combinedPopulation.get(j);
                final XY iPosition = iPerson.getPosition();
                final XY jPosition = jPerson.getPosition();
                final int deltaX = Math.abs(iPosition.getX() - jPosition.getX());
                final int deltaY = Math.abs(iPosition.getY() - jPosition.getY());
                final int distance = deltaX + deltaY;
                if (distance <= this.scenario.getParameters().getInfectionRadius()) {
                    if (iPerson.isInfectious() && iPerson.isCoughing() && jPerson.isBreathing()) {
                        jPerson.infect();
                    }
                    if (jPerson.isInfectious() && jPerson.isCoughing() && iPerson.isBreathing()) {
                        iPerson.infect();
                    }
                }
            }
        }

        this.patchPopulation = combinedPopulation.stream().filter(person -> patchArea.contains(person.getPosition())).toList();

        // we need to collect statistics and extend the recorded trace
        // +1 to offset 0-based tick counting. 0th tick reserved for state of simulation
        // and is done before the simulation's start in constructor
        this.extendOutput(tickNumber + 1);
    }

    private void doSynchronization() {
        synchronizeInnerPaddings();
        combinedPopulation.clear();
        readFromOuterPaddingsToCombinedPopulation();

        combinedPopulation = Utils.merge(combinedPopulation, patchPopulation,
                Comparator.comparing(Person::getId));
    }

    /**
     * Filters persons in specific part of the patch area.
     *
     * @param area Area where to look for people.
     * @return Returns people located in given area.
     */
    private List<Person> extractPopulationInArea(Rectangle area) {
        List<Person> populationInArea = new ArrayList<>();

        for (Person person : patchPopulation) {
            if (area.contains(person.getPosition())) {
                populationInArea.add(person);
            }
        }
        return populationInArea;
    }

    /**
     * Writes up-to date information about the population in inner paddings.
     */
    private void synchronizeInnerPaddings() {
        for (PaddingBuffer padding : innerPaddings) {
            List<Person> populationInPadding = extractPopulationInArea(padding.getArea());

            padding.write(populationInPadding);
        }
    }

    /**
     * Retrieves new population information for each of the outer paddings.
     * Stores it into `combinedPopulation`.
     */
    private void readFromOuterPaddingsToCombinedPopulation() {

        for (PaddingBuffer padding : outerPaddings) {
            List<Person> paddingPopulation = padding.read(this);
            if (combinedPopulation.isEmpty()) {
                combinedPopulation = paddingPopulation;
                continue;
            }

            combinedPopulation = Utils.merge(combinedPopulation, paddingPopulation,
                    Comparator.comparing(Person::getId));

        }
    }

    /**
     * @return Returns statistics for current tick.
     */
    private Map<String, Statistics> getTickStatistics() {
        Map<String, Statistics> tickStatistics = new HashMap<>();
        for (Map.Entry<String, Query> entry : queriesInPaddedArea.entrySet()) {
            final Query query = entry.getValue();
            Statistics statisticsForQuery = new Statistics(
                    this.patchPopulation.stream().filter(
                            (Person person) -> person.isSusceptible()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    this.patchPopulation.stream().filter(
                            (Person person) -> person.isInfected()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    this.patchPopulation.stream().filter(
                            (Person person) -> person.isInfectious()
                                    && query.getArea().contains(person.getPosition())
                    ).count(),
                    this.patchPopulation.stream().filter(
                            (Person person) -> person.isRecovered()
                                    && query.getArea().contains(person.getPosition())
                    ).count()
            );
            tickStatistics.put(entry.getKey(), statisticsForQuery);
        }

        return tickStatistics;
    }

    /**
     * @return Returns trace for current tick.
     */
    private List<PersonInfoWithId> getTickTrace() {
        if (!this.scenario.getTrace()) {
            return List.of();
        }

        return this.patchPopulation.stream()
                .map(person ->
                        new PersonInfoWithId(person.getInfo(), person.getId())
                )
                .collect(Collectors.toList());
    }

    /**
     * Sends output for given tick to output queue.
     * @param tick Tick for which output is sent.
     */
    private void extendOutput(int tick) {
        outputQueue.enqueue(
                new OutputEntry(
                        tick,
                        getTickStatistics(),
                        getTickTrace()
                )
        );
    }
}
