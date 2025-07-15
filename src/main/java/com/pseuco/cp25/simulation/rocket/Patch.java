package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.*;
import com.pseuco.cp25.simulation.common.Context;
import com.pseuco.cp25.simulation.common.Person;
import com.pseuco.cp25.simulation.common.Simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Patch implements Simulation, Context {
    private final List<PaddingBuffer> innerPaddings = new ArrayList<>();
    private final List<PaddingBuffer> outerPaddings = new ArrayList<>();
    private final int cycleDuration;
    private final List<Person> combinedPopulation;
    private final Rectangle patchArea;
    private final Rectangle paddedArea;
    private final Scenario scenario;
    private final List<TraceEntry> trace = new ArrayList<>();
    private final Map<String, List<Statistics>> statistics = new HashMap<>();
    private List<Person> patchPopulation;


    public Patch(List<Person> patchPopulation, Rectangle patchArea, Rectangle paddedArea,
                 int cycleDuration, Scenario scenario) {
        this.patchPopulation = patchPopulation.stream().map(person -> person.clone(this)).toList();
        this.patchArea = patchArea;
        this.paddedArea = paddedArea;
        this.cycleDuration = cycleDuration;
        this.scenario = scenario;

        this.initializeStatistics();
        this.combinedPopulation = new ArrayList<>();


    }

    private void initializeStatistics() {
        // we initialize the map we use to collect the necessary statistics
        for (String queryKey : this.scenario.getQueries().keySet()) {
            this.statistics.put(queryKey, new ArrayList<>());
        }
    }

    public void tick() {
        for (Person person : combinedPopulation) {
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

    public void addInnerPadding(PaddingBuffer paddingBuffer) {
        innerPaddings.add(paddingBuffer);
    }

    public void addOuterPadding(PaddingBuffer paddingBuffer) {
        outerPaddings.add(paddingBuffer);
    }

    public Rectangle getPatchArea() {
        return patchArea;
    }


    @Override
    public Rectangle getGrid() {
        return paddedArea;
    }

    @Override
    public List<Rectangle> getObstacles() {
        return this.scenario.getObstacles();
    }

    @Override
    public List<Person> getPopulation() {
        return combinedPopulation;
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
            this.trace.add(new TraceEntry(this.patchPopulation.stream().map(Person::getInfo).collect(Collectors.toList())));
        }

        this.extendStatistics();
    }

    @Override
    public Output getOutput() {
        return new Output(this.scenario, this.trace, this.statistics);
    }

    // Note that since tick starts with 0, synchronization will happen as the first thing.
    @Override
    public void run() {
        for (int tick = 0; tick < this.scenario.getTicks(); tick++) {
            if (tick % cycleDuration == 0) {
                doSynchronization();
            }
            this.tick();
        }
    }
}
