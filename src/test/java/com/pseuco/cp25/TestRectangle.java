package com.pseuco.cp25;

import com.pseuco.cp25.model.Rectangle;
import com.pseuco.cp25.model.XY;
import com.pseuco.cp25.simulation.rocket.Utils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TestRectangle {

    @Test
    public void testOverlaps() {
        Rectangle base = new Rectangle(new XY(5, 10), new XY(3, 7));
        assertTrue(
                base.overlaps(new Rectangle(new XY(4, 9), new XY(120, 42)))
        );
        assertTrue(
                base.overlaps(new Rectangle(new XY(6, 8), new XY(1, 3)))
        );
        assertFalse(
                base.overlaps(new Rectangle(new XY(6, 8), new XY(1, 2)))
        );
    }

    @Test
    public void testRectangleMinusRectangle() {
        Rectangle big = new Rectangle(
                new XY(0, 0), new XY(7, 5)
        );
        Rectangle small = new Rectangle(
                new XY(1, 1), new XY(3, 2)
        );


        List<Rectangle> paddingRectangles = Utils.rectangleMinusRectangle(big, small);

        for (Rectangle rectangle : paddingRectangles) {
            assertTrue(rectangle.overlaps(big));
            assertFalse(rectangle.overlaps(small));
        }

        assertEquals(4, paddingRectangles.size());

        Rectangle top = paddingRectangles.get(0);
        assertEquals(new XY(0, 0), top.getTopLeft());
        assertEquals(new XY(7, 1), top.getBottomRight());


        Rectangle left = paddingRectangles.get(1);
        assertEquals(new XY(0, 0), left.getTopLeft());
        assertEquals(new XY(1, 5), left.getBottomRight());

        Rectangle bottom = paddingRectangles.get(2);
        assertEquals(new XY(0, 3), bottom.getTopLeft());
        assertEquals(new XY(7, 5), bottom.getBottomRight());


        Rectangle right = paddingRectangles.get(3);
        assertEquals(new XY(4, 0), right.getTopLeft());
        assertEquals(new XY(7, 5), right.getBottomRight());
    }

}
