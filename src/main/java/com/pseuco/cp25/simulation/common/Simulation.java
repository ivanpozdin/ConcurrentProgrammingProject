package com.pseuco.cp25.simulation.common;

import com.pseuco.cp25.model.Output;

/**
 * A common interface to be implemented by simulation engines.
 */
public interface Simulation extends Runnable {

    public Output getOutput();
}
