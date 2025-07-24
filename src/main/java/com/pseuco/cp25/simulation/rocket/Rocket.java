package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.*;
import com.pseuco.cp25.simulation.common.Person;
import com.pseuco.cp25.simulation.common.Simulation;
import com.pseuco.cp25.validator.InsufficientPaddingException;
import com.pseuco.cp25.validator.Validator;

import java.util.*;

/**
 * Your implementation shall go into this class.
 *
 * <p>
 * This class has to implement the <em>Simulation</em> interface.
 * </p>
 */
public class Rocket implements Simulation {
    private final int cycleDuration;
    private final Scenario scenario;
    private final int padding;
    private final List<Patch> patches = new ArrayList<>();
    private final List<Person> initialPopulation = new ArrayList<>();

    // Statistics-related fields
    private final int statsLength;
    private final Map<String, List<Statistics>> totalStatistics = new HashMap<>();
    private TraceEntry[] totalTrace;
    private final MonitorQueue<OutputEntry> outputQueue = new MonitorQueue<>();
    private final int[] outputCounters;

    private final List<Thread> threads = new ArrayList<>();
    Validator validator;

    /**
     * Constructs a rocket with the given parameters.
     *
     * <p>
     * You must not change the signature of this constructor.
     * </p>
     *
     * <p>
     * Throw an insufficient padding exception if and only if the padding is
     * insufficient. Hint: Depending on the parameters, some amount of padding
     * is required even if one only computes one tick concurrently. The padding
     * is insufficient if the provided padding is below this minimal required
     * padding.
     * </p>
     *
     * @param scenario  The scenario to simulate.
     * @param padding   The padding to be used.
     * @param validator The validator to be called.
     */
    public Rocket(Scenario scenario, int padding, Validator validator) throws InsufficientPaddingException {
        this.scenario = scenario;
        this.padding = padding;
        this.validator = validator;
        this.statsLength = scenario.getTicks() + 1;
        this.outputCounters = new int[statsLength];
        Arrays.fill(outputCounters, 0);

        this.cycleDuration = getTicks();
        if (cycleDuration == 0) {
            throw new InsufficientPaddingException(padding);
        }

        populate();
        createPatches();
        createPaddings();
        initializeStatistics();

        if (scenario.getTrace()) {
            totalTrace = new TraceEntry[statsLength];
        } else {
            totalTrace = new TraceEntry[0];
        }
    }

    private int getTicks() {
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
        return new Output(this.scenario, Arrays.asList(totalTrace), this.totalStatistics);
    }

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

    private void createPatches() {
        Iterator<Rectangle> patchesIterator = Utils.getPatches(scenario);

        while (patchesIterator.hasNext()) {
            Rectangle patchArea = patchesIterator.next();
            Rectangle paddedArea = Utils.getPaddedArea(padding, patchArea, scenario.getGrid());

            List<Person> patchPopulation = initialPopulation.stream().filter(person -> patchArea.contains(person.getPosition())).toList();
            patches.add(
                    new Patch(patchPopulation,
                            patchArea,
                            paddedArea,
                            cycleDuration,
                            scenario,
                            patches.size(),
                            validator,
                            outputQueue
                    )
            );
        }
    }

    private void createPaddings() {
        for (Patch outerPatch : patches) {

            for (Patch innerPatch : patches) {
                if (innerPatch == outerPatch) continue;
                if (!innerPatch.getPatchArea().overlaps(outerPatch.getPaddedArea())) continue;

                Rectangle intersectionOfPaddingAndPatch =
                        outerPatch.getPaddedArea().intersect(innerPatch.getPatchArea());

                if (!com.pseuco.cp25.simulation.common.Utils.mayPropagateFrom(scenario,
                        intersectionOfPaddingAndPatch, outerPatch.getPatchArea())) continue;

                PaddingBuffer paddingBuffer =
                        new PaddingBuffer(intersectionOfPaddingAndPatch);

                innerPatch.addInnerPadding(paddingBuffer);
                outerPatch.addOuterPadding(paddingBuffer);
            }
        }

    }

    private void initializeStatistics() {
        for (String key : this.scenario.getQueries().keySet()) {
            List<Statistics> initializedArray = new ArrayList<>(statsLength);
            for (int i = 0; i < statsLength; i++) {
                initializedArray.add(new Statistics(0, 0, 0, 0));
            }

            totalStatistics.put(key, initializedArray);
        }
    }

    private void collectOutput() {
        // Collect output
        List<List<PersonInfoWithId>> trace = new ArrayList<>(statsLength);
        if (scenario.getTrace()) {
            for (int i = 0; i < statsLength; i++) {
                trace.add(new ArrayList<>());
            }
        }

        for (int i = 0; i < statsLength * patches.size(); i++) {
            OutputEntry entry = outputQueue.dequeue();
            outputCounters[entry.tick()]++;

            // Merge statistics
            collectPatchStatistics(entry.statisticsForTick(), entry.tick());

            // If trace disabled - skip
            if (!scenario.getTrace()) {
                continue;
            }

            // Process traces
            trace.get(entry.tick()).addAll(entry.traceForTick());

            if (outputCounters[entry.tick()] == patches.size()) {
                List<PersonInfoWithId> sorted = trace.get(entry.tick());
                sorted.sort(Comparator.comparing(PersonInfoWithId::id));
                TraceEntry traceEntry = new TraceEntry(sorted.stream().map(PersonInfoWithId::personInfo).toList());
                totalTrace[entry.tick()] = traceEntry;
            }
        }
    }

    private void collectPatchStatistics(Map<String, Statistics> patchStatsMap, int tick) {
        for (String key : patchStatsMap.keySet()) {
            Statistics mergedStatistics = Utils.mergeStatistics(
                    totalStatistics.get(key).get(tick), patchStatsMap.get(key)
            );
            totalStatistics.get(key).set(tick, mergedStatistics);
        }
    }
}
