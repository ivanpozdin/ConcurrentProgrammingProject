package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.*;
import com.pseuco.cp25.simulation.common.Person;
import com.pseuco.cp25.simulation.common.Simulation;
import com.pseuco.cp25.validator.DummyValidator;
import com.pseuco.cp25.validator.Validator;

import java.util.*;

/**
 * The implementation of assignment 2 should go here.
 *
 * <p>
 * This class has to implement the <em>Simulation</em> interface.
 * </p>
 */
public class Starship implements Simulation {
    private final int cycleDuration;
    private final Scenario scenario;
    private final int padding;
    private final List<Patch> patches = new ArrayList<>();
    private final List<Person> initialPopulation = new ArrayList<>();

    // Statistics-related fields
    private final int statsLength;
    private final Map<String, List<Statistics>> totalStatistics = new HashMap<>();
    private final List<TraceEntry> totalTrace;
    private final List<MonitorQueue<OutputEntry>> outputQueues;

    private final List<Thread> threads = new ArrayList<>();
    Validator validator;
    /**
     * Constructs a starship with the given parameters.
     *
     * <p>
     * You must not change the signature of this constructor.
     * </p>
     *
     * @param scenario The scenario to simulate.
     * @param padding Optional padding.
     */
    public Starship(Scenario scenario, int padding) {
        this.scenario = scenario;
        this.statsLength = scenario.getTicks() + 1;
        outputQueues = new ArrayList<>(scenario.getNumberOfPatches());

        int tempPadding = padding;
        int tempCycle = getTicks(tempPadding);
        while (tempCycle == 0) {
            tempPadding++;
            tempCycle = getTicks(tempPadding);
        }
        this.cycleDuration = tempCycle;
        this.padding = tempPadding;
        this.validator = new DummyValidator();

        GridManager gridManager = new GridManager(scenario);
        populate();
        createPatches();
        createPaddings(gridManager);
        initializeStatistics();

        if (scenario.getTrace()) {
            totalTrace = new ArrayList<>();
        } else {
            totalTrace = new ArrayList<>(statsLength);
        }
    }

    /**
     * Calculates cycle's duration.
     *
     * @return Cycle duration.
     */
    private int getTicks(int padding) {
        int t = 1;
        while (padding >= movementUncertainty(t) + infectionUncertainty(t)) {
            t++;
        }
        return t - 1;
    }

    private int movementUncertainty(int ticks) {
        return 2 * ticks;
    }

    private int infectionUncertainty(int ticks) {
        int incubationTime = scenario.getParameters().getIncubationTime();
        int radius = scenario.getParameters().getInfectionRadius();
        return Math.ceilDiv(ticks, incubationTime) * radius;
    }

    @Override
    public Output getOutput() {
        return new Output(this.scenario, totalTrace, this.totalStatistics);
    }

    /**
     * Runs the simulation.
     * Starts all patches' threads.
     * Collects patches' output while they simulate.
     */
    @Override
    public void run() {
        for (Patch patch : patches) {
            Thread thread = new Thread(patch);
            threads.add(thread);
            thread.start();
        }

        collectOutput();

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void populate() {
        // we populate the context with persons based on the respective info objects.
        // As context null is provided since patches will reassign context themselves.
        int id = 0;
        for (PersonInfo personInfo : this.scenario.getPopulation()) {
            this.initialPopulation.add(new Person(id, null, this.scenario.getParameters(), personInfo));
            id++;
        }
    }

    /**
     * Iterates over patches as defined in the scenario and creates associated Patch objects.
     * Stores patch objects in the list of patches.
     * For each patch creates a separate output queue that patch can write output to after every
     * tick.
     * Stores the queue into a list of queues to later retrieve outputs.
     */
    private void createPatches() {
        Iterator<Rectangle> patchesIterator = Utils.getPatches(scenario);

        while (patchesIterator.hasNext()) {
            Rectangle patchArea = patchesIterator.next();
            Rectangle paddedArea = Utils.getPaddedArea(padding, patchArea, scenario.getGrid());
            MonitorQueue<OutputEntry> outputQueueForPatch = new MonitorQueue<>();
            outputQueues.add(outputQueueForPatch);

            List<Person> patchPopulation = initialPopulation.stream().filter(person -> patchArea.contains(person.getPosition())).toList();
            patches.add(
                    new Patch(patchPopulation,
                            patchArea,
                            paddedArea,
                            cycleDuration,
                            scenario,
                            patches.size(),
                            validator,
                            outputQueueForPatch
                    )
            );
        }
    }

    /**
     * Iterates over patches created via createPatches() and initializes for each of them
     * associated paddings intersected with other patches.
     * Stores created PatchBuffers representing pieces of geometric padding into correspoding
     * innerPaddings or outerPaddings of the appropriate patch.
     */
    private void createPaddings(GridManager gridManager) {
        boolean hasObstacles = !scenario.getObstacles().isEmpty();
        for (Patch outerPatch : patches) {
            for (Patch innerPatch : patches) {
                if (innerPatch == outerPatch) continue;
                if (!innerPatch.getPatchArea().overlaps(outerPatch.getPaddedArea())) continue;

                Rectangle intersectionOfPaddingAndPatch =
                        outerPatch.getPaddedArea().intersect(innerPatch.getPatchArea());

                if (hasObstacles) {
                    if (!gridManager.mayPropagateFrom(scenario,
                            intersectionOfPaddingAndPatch, outerPatch.getPatchArea())) {
                        continue;
                    }
                }

                PaddingBuffer paddingBuffer =
                        new PaddingBuffer(intersectionOfPaddingAndPatch);

                innerPatch.addInnerPadding(paddingBuffer);
                outerPatch.addOuterPadding(paddingBuffer);
            }
        }
    }

    /**
     * Fills a statistics list with an empty Statistics object to later merge them with actual
     * statistics retrieved from the patches.
     */
    private void initializeStatistics() {
        for (String key : this.scenario.getQueries().keySet()) {
            List<Statistics> initializedArray = new ArrayList<>(statsLength);
            for (int i = 0; i < statsLength; i++) {
                initializedArray.add(new Statistics(0, 0, 0, 0));
            }

            totalStatistics.put(key, initializedArray);
        }
    }

    /**
     * Collects output from output queues and merges them into final output.
     */
    private void collectOutput() {

        for (int i = 0; i < statsLength; i++) {
            List<PersonInfoWithId> trace = new ArrayList<>();

            for (MonitorQueue<OutputEntry> outputQueue : outputQueues) {
                OutputEntry entry = outputQueue.dequeue();

                // Merge statistics
                collectPatchStatistics(entry.statisticsForTick(), entry.tick());

                // If trace disabled - skip
                if (!scenario.getTrace()) continue;

                // Process traces

                //  Merge sorted traces
                trace = Utils.merge(
                        trace,
                        entry.traceForTick(),
                        Comparator.comparing(PersonInfoWithId::id)
                );
            }

            // If trace disabled - skip
            if (!scenario.getTrace()) continue;

            TraceEntry traceEntry = new TraceEntry(trace.stream().map(PersonInfoWithId::personInfo).toList());
            totalTrace.add(traceEntry);
        }
    }

    /**
     * Updates totalStatistics with provided statistic.
     *
     * @param patchStatsMap statistics from some patch to merge with totalStatistics.
     * @param tick          Tick for which statistics is provided.
     */
    private void collectPatchStatistics(Map<String, Statistics> patchStatsMap, int tick) {
        for (String key : patchStatsMap.keySet()) {
            Statistics mergedStatistics = Utils.mergeStatistics(
                    totalStatistics.get(key).get(tick), patchStatsMap.get(key)
            );
            totalStatistics.get(key).set(tick, mergedStatistics);
        }
    }
}
