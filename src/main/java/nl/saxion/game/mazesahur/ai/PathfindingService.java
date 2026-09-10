package nl.saxion.game.mazesahur.ai;

import nl.saxion.game.mazesahur.world.Maze;

import java.util.*;

/**
 * A* pathfinding service for navigating the maze.
 * Provides static methods for finding optimal paths and checking line of sight.
 * Includes rail-based pathfinding with rotation constraints.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public final class PathfindingService {

    private PathfindingService() {
        throw new AssertionError("Cannot instantiate PathfindingService");
    }

    /**
     * Finds a path using the rail network with rotation constraints.
     * This creates rollercoaster-like movement where the entity can only
     * rotate at junctions and follows rail directions.
     *
     * @param railNetwork The rail network to navigate
     * @param startX Start grid X coordinate
     * @param startZ Start grid Z coordinate
     * @param endX End grid X coordinate
     * @param endZ End grid Z coordinate
     * @param currentDirection Current facing direction (can be null for initial pathfinding)
     * @return List of RailPathNode representing the path with directions, or empty list if no path exists
     */
    public static List<RailPathNode> findRailPath(final RailNetwork railNetwork,
                                                   final int startX, final int startZ,
                                                   final int endX, final int endZ,
                                                   final RailDirection currentDirection) {
        final RailNode startNode = railNetwork.getNode(startX, startZ);
        final RailNode endNode = railNetwork.getNode(endX, endZ);

        if (startNode == null || endNode == null) {
            return Collections.emptyList();
        }

        final PriorityQueue<RailSearchNode> openSet = new PriorityQueue<>(
            Comparator.comparingDouble(n -> n.fScore));
        final Set<String> openSetKeys = new HashSet<>();
        final Set<String> closedSet = new HashSet<>();
        final Map<String, RailSearchNode> allNodes = new HashMap<>();

        // Create initial nodes for all possible starting directions
        if (currentDirection != null) {
            final RailSearchNode initial = new RailSearchNode(startNode, currentDirection);
            initial.gScore = 0;
            initial.fScore = heuristic(startX, startZ, endX, endZ);
            openSet.add(initial);
            allNodes.put(initial.key(), initial);
            openSetKeys.add(initial.key());
        } else {
            // If no current direction, try all possible directions
            for (final RailDirection dir : RailDirection.values()) {
                if (startNode.hasConnection(dir)) {
                    final RailSearchNode initial = new RailSearchNode(startNode, dir);
                    initial.gScore = 0;
                    initial.fScore = heuristic(startX, startZ, endX, endZ);
                    openSet.add(initial);
                    allNodes.put(initial.key(), initial);
                    openSetKeys.add(initial.key());
                }
            }
        }

        while (!openSet.isEmpty()) {
            final RailSearchNode current = openSet.poll();
            openSetKeys.remove(current.key());

            // Check if we reached the end
            if (current.node.getX() == endX && current.node.getZ() == endZ) {
                return reconstructRailPath(current);
            }

            closedSet.add(current.key());

            // Explore all possible directions from current node
            for (final Map.Entry<RailDirection, RailNode> entry : current.node.getConnections().entrySet()) {
                final RailDirection moveDirection = entry.getKey();
                final RailNode neighbor = entry.getValue();

                final String neighborKey = RailSearchNode.makeKey(neighbor, moveDirection);
                if (closedSet.contains(neighborKey)) {
                    continue;
                }

                // Calculate rotation cost
                final double rotationCost = current.direction.rotationCost(moveDirection);
                final double tentativeGScore = current.gScore + rotationCost;

                RailSearchNode neighborNode = allNodes.get(neighborKey);
                if (neighborNode == null) {
                    neighborNode = new RailSearchNode(neighbor, moveDirection);
                    allNodes.put(neighborKey, neighborNode);
                }

                if (tentativeGScore < neighborNode.gScore) {
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeGScore;
                    neighborNode.fScore = tentativeGScore + heuristic(
                        neighbor.getX(), neighbor.getZ(), endX, endZ);

                    if (openSetKeys.contains(neighborKey)) {
                        openSet.remove(neighborNode);
                    }
                    openSet.add(neighborNode);
                    openSetKeys.add(neighborKey);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Finds the shortest path from start to end using A* algorithm.
     *
     * @param maze  The maze to navigate
     * @param startX Start grid X coordinate
     * @param startZ Start grid Z coordinate
     * @param endX   End grid X coordinate
     * @param endZ   End grid Z coordinate
     * @return List of grid coordinates representing the path, or empty list if no path exists
     */
    public static List<int[]> findPath(final Maze maze, final int startX, final int startZ,
                                        final int endX, final int endZ) {
        final PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        final Set<String> openSetKeys = new HashSet<>();
        final Set<String> closedSet = new HashSet<>();
        final Map<String, Node> allNodes = new HashMap<>();

        final Node startNode = new Node(startX, startZ);
        startNode.gScore = 0;
        startNode.fScore = heuristic(startX, startZ, endX, endZ);

        openSet.add(startNode);
        allNodes.put(startNode.key(), startNode);
        openSetKeys.add(startNode.key());

        while (!openSet.isEmpty()) {
            final Node current = openSet.poll();
            openSetKeys.remove(current.key());

            if (current.x == endX && current.z == endZ) {
                return reconstructPath(current);
            }

            closedSet.add(current.key());

            // Check 4 neighbors (N, E, S, W)
            final int[][] neighbors = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            for (final int[] dir : neighbors) {
                final int nx = current.x + dir[0];
                final int nz = current.z + dir[1];

                if (maze.isWall(nx, nz) || closedSet.contains(Node.makeKey(nx, nz))) {
                    continue;
                }

                final double tentativeGScore = current.gScore + 1;

                final String neighborKey = Node.makeKey(nx, nz);
                Node neighbor = allNodes.get(neighborKey);

                if (neighbor == null) {
                    neighbor = new Node(nx, nz);
                    allNodes.put(neighborKey, neighbor);
                }

                if (tentativeGScore < neighbor.gScore) {
                    neighbor.parent = current;
                    neighbor.gScore = tentativeGScore;
                    neighbor.fScore = tentativeGScore + heuristic(nx, nz, endX, endZ);

                    if (openSetKeys.contains(neighborKey)) {
                        openSet.remove(neighbor);
                    }
                    openSet.add(neighbor);
                    openSetKeys.add(neighborKey);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Checks if there is a clear line of sight between two world positions.
     *
     * @param maze     The maze
     * @param startX   Start world X coordinate
     * @param startZ   Start world Z coordinate
     * @param endX     End world X coordinate
     * @param endZ     End world Z coordinate
     * @param cellSize Size of each grid cell
     * @return True if line of sight is clear
     */
    public static boolean hasLineOfSight(final Maze maze, final float startX, final float startZ,
                                          final float endX, final float endZ, final float cellSize) {
        final float dx = endX - startX;
        final float dz = endZ - startZ;
        final float distance = (float) Math.sqrt(dx * dx + dz * dz);

        final int steps = (int) (distance / (cellSize * 0.5f));

        for (int i = 0; i <= steps; i++) {
            final float t = (float) i / steps;
            final float x = startX + dx * t;
            final float z = startZ + dz * t;

            final int gridX = (int) (x / cellSize);
            final int gridZ = (int) (z / cellSize);

            if (maze.isWall(gridX, gridZ)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Manhattan distance heuristic for A*.
     */
    private static double heuristic(final int x1, final int z1, final int x2, final int z2) {
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
    }

    /**
     * Reconstructs the path from the end node.
     */
    private static List<int[]> reconstructPath(Node node) {
        final List<int[]> path = new ArrayList<>();
        while (node != null) {
            path.add(new int[]{node.x, node.z});
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Reconstructs the rail path from the end node.
     */
    private static List<RailPathNode> reconstructRailPath(RailSearchNode node) {
        final List<RailPathNode> path = new ArrayList<>();
        while (node != null) {
            path.add(new RailPathNode(node.node.getX(), node.node.getZ(), node.direction));
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Internal node class for A* pathfinding.
     */
    private static class Node {
        final int x;
        final int z;
        Node parent;
        double gScore = Double.MAX_VALUE;
        double fScore = Double.MAX_VALUE;

        Node(final int x, final int z) {
            this.x = x;
            this.z = z;
        }

        String key() {
            return makeKey(x, z);
        }

        static String makeKey(final int x, final int z) {
            return x + "," + z;
        }
    }

    /**
     * Internal node class for rail-based A* pathfinding.
     * Includes direction to track rotation costs.
     */
    private static class RailSearchNode {
        final RailNode node;
        final RailDirection direction;
        RailSearchNode parent;
        double gScore = Double.MAX_VALUE;
        double fScore = Double.MAX_VALUE;

        RailSearchNode(final RailNode node, final RailDirection direction) {
            this.node = node;
            this.direction = direction;
        }

        String key() {
            return makeKey(node, direction);
        }

        static String makeKey(final RailNode node, final RailDirection direction) {
            return node.getX() + "," + node.getZ() + ":" + direction.name();
        }
    }

    /**
     * Represents a node in a rail path with position and direction.
     */
    public static class RailPathNode {
        private final int x;
        private final int z;
        private final RailDirection direction;

        public RailPathNode(final int x, final int z, final RailDirection direction) {
            this.x = x;
            this.z = z;
            this.direction = direction;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        public RailDirection getDirection() {
            return direction;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + z + " -> " + direction + ")";
        }
    }
}
