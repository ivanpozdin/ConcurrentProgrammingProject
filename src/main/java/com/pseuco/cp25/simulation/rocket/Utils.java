package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.Rectangle;
import com.pseuco.cp25.model.Scenario;
import com.pseuco.cp25.model.Statistics;
import com.pseuco.cp25.model.XY;

import java.util.Iterator;

/**
 * Some useful utilities for the concurrent implementation.
 */
public class Utils {

    /**
     * Returns an iterator iterating over the patches specified in the scenario.
     *
     * @param scenario The scenario.
     * @return An iterator over the patches.
     */
    static public Iterator<Rectangle> getPatches(Scenario scenario) {
        return new PatchesIterator(scenario);
    }


    /**
     * Computes a padded area around a given rectangle, constrained by a grid.
     *
     * @param padding The padding size to add around the rectangle.
     * @param area    The original rectangle to pad.
     * @param grid    The grid rectangle to constrain the padded area.
     * @return A new rectangle representing the padded area, intersected with the grid.
     */
    static public Rectangle getPaddedArea(int padding, Rectangle area, Rectangle grid) {
        XY topLeft = area.getTopLeft().sub(padding);
        XY size = area.getSize().add(2 * padding);

        return new Rectangle(topLeft, size).intersect(grid);
    }

    /**
     * Merges two statistics objects by summing up their respective fields.
     *
     * @param stats1 The first statistics object.
     * @param stats2 The second statistics object.
     * @return A new Statistics object with summed values.
     */
    public static Statistics mergeStatistics(Statistics stats1, Statistics stats2) {
        return new Statistics(
                stats1.getSusceptible() + stats2.getSusceptible(),
                stats1.getInfected() + stats2.getInfected(),
                stats1.getInfectious() + stats2.getInfectious(),
                stats1.getRecovered() + stats2.getRecovered()
        );
    }

    static private class PatchesIterator implements Iterator<Rectangle> {

        private final Scenario scenario;

        private final Iterator<Integer> yIterator;

        private final int maxX;
        private final int maxY;

        private Iterator<Integer> xIterator;

        private int currentY = 0;

        private int lastX = 0;
        private int lastY = 0;

        public PatchesIterator(final Scenario scenario) {
            this.scenario = scenario;
            this.yIterator = this.scenario.getPartition().getY().iterator();
            this.maxX = this.scenario.getGridSize().getX();
            this.maxY = this.scenario.getGridSize().getY();
            this.xIterator = this.scenario.getPartition().getX().iterator();
            if (this.yIterator.hasNext()) {
                this.currentY = this.yIterator.next();
            } else {
                this.currentY = this.maxY;
            }
        }

        @Override
        public boolean hasNext() {
            return this.lastY != this.maxY;
        }

        @Override
        public Rectangle next() {
            assert this.hasNext();
            if (!this.xIterator.hasNext()) {
                final XY topLeft = new XY(this.lastX, this.lastY);
                final XY bottomRight = new XY(this.maxX, this.currentY);
                this.lastY = this.currentY;
                if (this.yIterator.hasNext()) {
                    this.currentY = this.yIterator.next();
                } else {
                    this.currentY = this.maxY;
                }
                this.xIterator = this.scenario.getPartition().getX().iterator();
                this.lastX = 0;
                return new Rectangle(topLeft, bottomRight.sub(topLeft));
            }
            final int currentX = this.xIterator.next();
            final XY topLeft = new XY(this.lastX, this.lastY);
            final XY bottomRight = new XY(currentX, this.currentY);
            this.lastX = currentX;
            return new Rectangle(topLeft, bottomRight.sub(topLeft));
        }
    }
}
