package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.Rectangle;
import com.pseuco.cp25.model.Scenario;
import com.pseuco.cp25.model.XY;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
     * Computes the difference between two rectangles, returning a list of rectangles
     * that represent the area of the larger rectangle not covered by the smaller one.
     *
     * @param big   The larger rectangle.
     * @param small The smaller rectangle to subtract from the larger one.
     * @return A list of rectangles representing the uncovered areas.
     * (In counter-clockwise order starting from the top rectangle (if non-empty))
     */
    static public List<Rectangle> rectangleMinusRectangle(Rectangle big,
                                                          Rectangle small) {
        List<Rectangle> rectangles = new ArrayList<>();

        // Check that big rectangle covers the small one.
        if (!(big.intersect(small).equals(small))) return rectangles;

        if (big.getTopLeft().getY() != small.getTopLeft().getY()) {
            Rectangle top = new Rectangle(
                    big.getTopLeft(),
                    new XY(big.getSize().getX(), small.getTopLeft().getY() - big.getTopLeft().getY())
            );
            rectangles.add(top);
        }

        if (big.getTopLeft().getX() != small.getTopLeft().getX()) {
            Rectangle left = new Rectangle(
                    big.getTopLeft(),
                    new XY(small.getTopLeft().getX() - big.getTopLeft().getX(),
                            big.getSize().getY())
            );
            rectangles.add(left);
        }

        if (big.getBottomRight().getY() != small.getBottomRight().getY()) {
            XY smallBottomLeft = small.getBottomRight().sub(small.getSize().getX(), 0);

            Rectangle bottom = new Rectangle(
                    new XY(big.getTopLeft().getX(), smallBottomLeft.getY()),
                    new XY(big.getSize().getX(), big.getBottomRight().getY() - small.getBottomRight().getY())
            );
            rectangles.add(bottom);
        }

        if (big.getBottomRight().getX() != small.getBottomRight().getX()) {
            Rectangle right = new Rectangle(
                    new XY(small.getBottomRight().getX(), big.getTopLeft().getY()),
                    new XY(big.getBottomRight().getX() - small.getBottomRight().getX(), big.getSize().getY())
            );
            rectangles.add(right);
        }

        return rectangles;
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
