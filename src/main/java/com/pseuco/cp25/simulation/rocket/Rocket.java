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
    private final Map<String, List<Statistics>> totalStatistics = new HashMap<>();
    private final List<Person> initialPopulation = new ArrayList<>();
    private final List<Patch> patches = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    Validator validator;
    private List<TraceEntry> totalTrace;

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
        this.cycleDuration = getTicks();
        if (cycleDuration == 0) {
            throw new InsufficientPaddingException(padding);
        }

        this.scenario = scenario;
        this.padding = padding;
        this.validator = validator;

        populate();
        createPatches();
        createPaddings();
    }

    private int getTicks() {
        assert scenario != null;
        int infectiousRadius = scenario.getParameters().getInfectionRadius();
        int incubationTime = scenario.getParameters().getIncubationTime();

        if (infectiousRadius + 1 >= padding) return 0;

        int ticks = 0;
        int position = -1;

        while (position < padding) {
            if (position == -1)
                // the border-crossing scenario
                position += infectiousRadius + 2;
            else
                // otherwise just move and infect
                position += infectiousRadius + 1;

            // -1 to compensate for 0-based indexing of position
            if (position > padding - 1) break;

            ticks++;

            for (int i = 0; i < incubationTime - 1; i++) {
                position++;
                if (position > padding - 1) return ticks;
                ticks++;
            }
        }
        return ticks;
    }

    @Override
    public Output getOutput() {
        return new Output(this.scenario, this.totalTrace, this.totalStatistics);
    }

    @Override
    public void run() {
        for (Patch patch : patches) {
            Thread thread = new Thread(patch);
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        collectTraces();
        collectStatistics();
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
                            validator
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
                PaddingBuffer paddingBuffer =
                        new PaddingBuffer(intersectionOfPaddingAndPatch);

                innerPatch.addInnerPadding(paddingBuffer);
                outerPatch.addOuterPadding(paddingBuffer);
            }
        }

    }

    private void collectTraces() {
        List<List<PersonInfoWithId>> trace = new ArrayList<>(scenario.getTicks());
        for (int i = 0; i < scenario.getTicks(); i++) {
            trace.add(new ArrayList<>());
        }
        for (Patch patch : patches) {
            List<List<PersonInfoWithId>> patchTrace = patch.getTrace();

            assert (patchTrace.size() == scenario.getTicks());

            for (int i = 0; i < scenario.getTicks(); i++) {
                trace.get(i).addAll(patchTrace.get(i));
            }
        }
        for (int i = 0; i < scenario.getTicks(); i++) {
            trace.get(i).sort(Comparator.comparing(PersonInfoWithId::id));
        }
        totalTrace =
                trace.stream().map(list -> new TraceEntry(list.stream().map(PersonInfoWithId::personInfo).toList())).toList();

    }

    private void collectStatistics() {
        for (String key : this.scenario.getQueries().keySet()) {
            List<Statistics> initializedArray = new ArrayList<>(scenario.getTicks());
            for (int i = 0; i < scenario.getTicks(); i++) {
                initializedArray.add(new Statistics(0, 0, 0, 0));
            }

            totalStatistics.put(key, initializedArray);
        }

        for (Patch patch : patches) {
            Map<String, List<Statistics>> patchStatsMap = patch.getStatistics();

            for (String key : patchStatsMap.keySet()) {
                List<Statistics> mergedStats = totalStatistics.get(key);
                List<Statistics> patchStats = patchStatsMap.get(key);
                assert (mergedStats.size() == patchStats.size());

                for (int i = 0; i < mergedStats.size(); i++) {
                    mergedStats.set(
                            i,
                            Utils.mergeStatistics(
                                    mergedStats.get(i),
                                    patchStats.get(i)
                            )
                    );
                }
            }
        }
    }
}
