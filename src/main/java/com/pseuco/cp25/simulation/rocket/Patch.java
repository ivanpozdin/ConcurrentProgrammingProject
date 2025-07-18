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
 * PaddingBuffers, and communicating statistics at the end.
 */
public class Patch implements Runnable, Context {
    private final List<PaddingBuffer> innerPaddings = new ArrayList<>();
    private final List<PaddingBuffer> outerPaddings = new ArrayList<>();
    private final int cycleDuration;
    private final List<Person> combinedPopulation;
    private final Rectangle patchArea;
    private final Rectangle paddedArea;
    private final Scenario scenario;
    private final Map<String, List<Statistics>> statistics = new HashMap<>();
    private final List<List<PersonInfoWithId>> trace = new ArrayList<>();

    private final List<Rectangle> obstaclesInPaddedArea;

    private final int patchId;
    private final Validator validator;
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
            Validator validator
    ) {
        this.patchPopulation = patchPopulation.stream().map(person -> person.clone(this)).toList();
        this.patchArea = patchArea;
        this.paddedArea = paddedArea;
        this.cycleDuration = cycleDuration;
        this.scenario = scenario;
        this.patchId = patchId;
        this.validator = validator;

        this.initializeStatistics();
        this.extendOutput();
        this.combinedPopulation = new ArrayList<>();

        obstaclesInPaddedArea =
                scenario.getObstacles().stream().filter(obstacle -> obstacle.overlaps(paddedArea)).toList();
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
     * Sends statistics when all ticks of the scenario are simulated.
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
     * Returns the statistics collected during simulation.
     * To be called after simulation is finished.
     *
     * @return The statistics collected during simulation.
     */
    public Map<String, List<Statistics>> getStatistics() {
        return statistics;
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

    private void initializeStatistics() {
        // we initialize the map we use to collect the necessary statistics
        for (String queryKey : this.scenario.getQueries().keySet()) {
            this.statistics.put(queryKey, new ArrayList<>());
        }
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
        this.combinedPopulation.stream().forEach(Person::bustGhost);

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
        this.extendOutput();
    }

    private void doSynchronization() {
        synchronizeInnerPaddings();
        combinedPopulation.clear();
        readFromOuterPaddingsToCombinedPopulation();
        combinedPopulation.addAll(patchPopulation);
        combinedPopulation.sort(Comparator.comparing(Person::getId));
    }

    private List<Person> extractPopulationInArea(Rectangle area) {
        List<Person> populationInArea = new ArrayList<>();

        for (Person person : patchPopulation) {
            if (area.contains(person.getPosition())) {
                populationInArea.add(person);
            }
        }
        return populationInArea;
    }

    private void synchronizeInnerPaddings() {
        for (PaddingBuffer padding : innerPaddings) {
            List<Person> populationInPadding = extractPopulationInArea(padding.getArea());

            padding.write(populationInPadding);
        }
    }

    private void readFromOuterPaddingsToCombinedPopulation() {
        for (PaddingBuffer padding : outerPaddings) {
            List<Person> paddingPopulation = padding.read(this);
            combinedPopulation.addAll(paddingPopulation);
        }
    }


    private void extendStatistics() {
        // we collect statistics based on the current SI²R values
        for (Map.Entry<String, Query> entry : this.scenario.getQueries().entrySet()) {
            final Query query = entry.getValue();
            this.statistics.get(entry.getKey()).add(new Statistics(
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
            ));
        }
    }

    private void extendOutput() {
        // we extend the statists and the trace for the current tick
        if (this.scenario.getTrace()) {
            this.trace.add(
                    this.patchPopulation.stream()
                            .map(person ->
                                    new PersonInfoWithId(person.getInfo(), person.getId())
                            )
                            .collect(Collectors.toList())
            );
        }

        this.extendStatistics();
    }
}
