package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.Statistics;

import java.util.List;

public record OutputEntry(int tick, Statistics statistics, List<PersonInfoWithId> trace) {
}
