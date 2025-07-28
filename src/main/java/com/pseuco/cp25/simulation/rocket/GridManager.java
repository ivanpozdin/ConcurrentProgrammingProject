package com.pseuco.cp25.simulation.rocket;

import com.pseuco.cp25.model.*;

import java.util.*;

public class GridManager {
    private final Rectangle grid;
    private final Map<XY, Integer> cellToComponent = new HashMap<>();
    private final List<Set<XY>> components = new ArrayList<>();
    private final Set<Integer> emptyComponents;

    public GridManager(Scenario scenario) {
        this.grid = scenario.getGrid();
        int width = scenario.getGridSize().getX();
        int height = scenario.getGridSize().getY();

        // Initialize grid matrix without obstacles
        boolean[][] free = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                free[x][y] = true;
            }
        }

        // Mark all obstacles in out grid
        for (Rectangle rect : scenario.getObstacles()) {
            for (XY cell : rect) {
                free[cell.getX()][cell.getY()] = false;
            }
        }

        // Compute grid's components
        boolean[][] visited = new boolean[width][height];
        int currentId = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (free[x][y] && !visited[x][y]) {
                    Set<XY> component = new HashSet<>();
                    gridBFS(new XY(x, y), free, visited, component, currentId);
                    components.add(component);
                    currentId++;
                }
            }
        }

        // Find empty components
        emptyComponents = findEmptyComponentIds(scenario.getPopulation());
    }

    /**
     * BFS pass over the grid starting from a given point that forms a component
     */
    private void gridBFS(XY start, boolean[][] free, boolean[][] visited, Set<XY> component, int componentId) {
        Deque<XY> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start.getX()][start.getY()] = true;
        cellToComponent.put(start, componentId);

        while (!queue.isEmpty()) {
            XY curr = queue.poll();
            component.add(curr);
            for (Direction dir : Direction.values()) {
                if (dir == Direction.NONE) continue;

                XY neighbor = new XY(
                        dir.getVector().getX() + curr.getX(),
                        dir.getVector().getY() + curr.getY()
                );
                int nx = neighbor.getX();
                int ny = neighbor.getY();

                if (grid.contains(neighbor) && free[nx][ny] && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    cellToComponent.put(neighbor, componentId);
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Given a population calculates a set of empty components' IDs.
     * @return set of empty components' IDs
     */
    private Set<Integer> findEmptyComponentIds(List<PersonInfo> population) {
        boolean[] hasPerson = new boolean[components.size()];

        for (PersonInfo p : population) {
            int compId = getComponentId(p.getPosition());
            if (compId >= 0) {
                hasPerson[compId] = true;
            }
        }

        Set<Integer> emptyIds = new HashSet<>();
        for (int i = 0; i < hasPerson.length; i++) {
            if (!hasPerson[i]) {
                emptyIds.add(i);
            }
        }

        return emptyIds;
    }

    /**
     * An optimized version of the method with the same name from common.Utils class
     */
    public boolean mayPropagateFrom(
            final Scenario scenario,
            final Rectangle source,
            final Rectangle target) {
        final Set<XY> region = new HashSet<>();
        final Set<XY> frontier = new HashSet<>();
        for (final XY targetCell : target) {
            if (!scenario.onObstacle(targetCell)) {
                frontier.add(targetCell);
            }
        }
        final int infectionRadius = scenario.getParameters().getInfectionRadius();
        while (!frontier.isEmpty()) {
            final XY cell = frontier.iterator().next();
            frontier.remove(cell);
            region.add(cell);
            // Process the neighbors of the current cell
            if (processNeighbors(scenario, cell, infectionRadius, frontier, region, source)) {
                return true;  // If any neighbor is part of the source, return true immediately
            }
        }

        // If no reachable source cells found, return false
        return false;
    }

    private boolean processNeighbors(
            Scenario scenario,
            XY cell,
            int infectionRadius,
            Set<XY> frontier,
            Set<XY> region,
            Rectangle source) {
        for (int deltaX = -infectionRadius; deltaX <= infectionRadius; deltaX++) {
            for (int deltaY = -infectionRadius; deltaY <= infectionRadius; deltaY++) {
                if (Math.abs(deltaX) + Math.abs(deltaY) <= infectionRadius
                        || (Math.abs(deltaX) <= 1 && Math.abs(deltaY) <= 1)) {
                    final XY neighbor = cell.add(deltaX, deltaY);
                    if (!region.contains(neighbor)
                            && scenario.getGrid().contains(neighbor)
                            && !scenario.onObstacle(neighbor)
                            // Additionally check if neighbour ends up in an empty component
                            && !emptyComponents.contains(getComponentId(neighbor))) {
                        // Return immediately if rectangles have connected
                        if (source.contains(neighbor)) {
                            return true;
                        }
                        frontier.add(neighbor);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns component ID that the provided cell is contained in.
     * If a cell does not belong to a component, returns -1.
     * @return component ID or -1 on no component ownership
     */
    public int getComponentId(XY cell) {
        return cellToComponent.getOrDefault(cell, -1);
    }
}
