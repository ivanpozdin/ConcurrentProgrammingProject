package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.Statistics;

import java.util.List;
import java.util.Map;

public record OutputEntry(int tick, Map<String, Statistics> statisticsForTick, List<PersonInfoWithId> traceForTick) {
}
