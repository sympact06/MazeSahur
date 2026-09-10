package nl.saxion.game.mazesahur.entity;

import com.badlogic.gdx.math.Vector3;
import nl.saxion.game.mazesahur.ai.PathfindingService;
import nl.saxion.game.mazesahur.ai.PathfindingService.RailPathNode;
import nl.saxion.game.mazesahur.ai.RailDirection;
import nl.saxion.game.mazesahur.ai.RailNetwork;
import nl.saxion.game.mazesahur.ai.RailNode;
import nl.saxion.game.mazesahur.config.GameConfig;
import nl.saxion.game.mazesahur.world.Maze;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents the enemy entity that chases the player.
 * Features intelligent AI with multiple behavior states.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class Enemy {
    private float speedMultiplier = 1.0f;

    /**
     * AI behavior states for the enemy.
     */
    public enum AIState {
        WANDERING,   // Moving randomly
        CHASING,     // Direct line of sight to player
        PURSUING,    // Going to last known position
        PATHFINDING  // Using A* to navigate maze
    }

    private final Vector3 position;
    private final Maze maze;
    private final Player player;
    private final Random random;
    private final RailNetwork railNetwork;

    // AI state
    private AIState currentState;
    private final Vector3 lastKnownPlayerPosition;
    private final Vector3 wanderTarget;
    private float timeSincePlayerSeen;
    private float pathUpdateTimer;
    private float wanderUpdateTimer;

    // Rail-based pathfinding
    private List<RailPathNode> currentPath;
    private int pathIndex;
    private RailDirection currentDirection;
    private RailDirection previousDirection;
    private float rotationProgress;
    private final Vector3 pathTargetPosition;

    // Movement
    private float yaw;

    // Stuck detection
    private final Vector3 lastPosition;
    private final Vector3 moveDirection = new Vector3();
    private final Vector3 nextPosition = new Vector3();
    private float stuckTimer;
    private static final float STUCK_THRESHOLD = 0.5f;

    /**
     * Creates a new enemy entity.
     *
     * @param maze The game maze
     * @param player Reference to the player
     */
    public Enemy(final Maze maze, final Player player) {
        this.maze = maze;
        this.player = player;
        this.position = new Vector3();
        this.random = new Random();
        this.railNetwork = new RailNetwork(maze);
        this.currentState = AIState.WANDERING;
        this.lastKnownPlayerPosition = new Vector3();
        this.wanderTarget = new Vector3();
        this.lastPosition = new Vector3();
        this.currentPath = new ArrayList<>();
        this.pathTargetPosition = new Vector3();
        this.timeSincePlayerSeen = GameConfig.ENEMY_CHASE_MEMORY_DURATION + 1;
        this.pathUpdateTimer = 0;
        this.wanderUpdateTimer = 0;
        this.pathIndex = 0;
        this.stuckTimer = 0;
        this.currentDirection = RailDirection.NORTH;
        this.previousDirection = RailDirection.NORTH;
        this.rotationProgress = 1.0f;
    }

    /**
     * Initializes the enemy at a valid spawn position far from the player.
     */
    public void initialize() {
        findValidSpawnPosition();
        lastPosition.set(position);

        // Initialize with a valid direction based on available connections
        final int[] grid = maze.worldToGrid(position.x, position.z);
        final RailNode startNode = railNetwork.getNode(grid[0], grid[1]);
        if (startNode != null && !startNode.getConnections().isEmpty()) {
            // Pick the first available direction
            currentDirection = startNode.getConnections().keySet().iterator().next();
            previousDirection = currentDirection;
        }

        // Set initial wander target
        updateWanderTargetRail();
        System.out.println("Enemy initialized at (" + grid[0] + ", " + grid[1]
            + ") facing " + currentDirection);
    }

    /**
     * Updates enemy AI and movement.
     *
     * @param delta Time since last frame
     */
    public void update(final float delta) {
        updateAI(delta);
        updateMovement(delta);
        updateSahurTransform();
    }

    /**
     * Updates AI behavior based on player visibility and state.
     */
    private void updateAI(final float delta) {
        // Check line of sight to player
        final boolean canSeePlayer = PathfindingService.hasLineOfSight(
            maze,
            position.x, position.z,
            player.getPosition().x, player.getPosition().z,
            Maze.CELL_SIZE
        );

        if (canSeePlayer) {
            timeSincePlayerSeen = 0;
            lastKnownPlayerPosition.set(player.getPosition());
            // Only switch to CHASING if not already chasing/pathfinding
            if (currentState != AIState.CHASING && currentState != AIState.PATHFINDING) {
                currentState = AIState.CHASING;
            }
        } else {
            timeSincePlayerSeen += delta;

            if (currentState == AIState.CHASING || currentState == AIState.PATHFINDING) {
                currentState = AIState.PURSUING;
            }

            if (timeSincePlayerSeen > GameConfig.ENEMY_CHASE_MEMORY_DURATION
                && currentState == AIState.PURSUING) {
                currentState = AIState.WANDERING;
                wanderUpdateTimer = 0;
            }
        }

        // Update behavior timers
        pathUpdateTimer += delta;
        wanderUpdateTimer += delta;
    }

    /**
     * Updates enemy movement based on current AI state.
     * Completely rewritten for rail-based movement.
     */
    private void updateMovement(final float delta) {
        // Determine what we should be moving toward
        Vector3 finalGoal = determineMovementGoal();

        // Check if we need a new path
        if (shouldRecalculatePath(finalGoal)) {
            calculateNewRailPath(finalGoal);
        }

        // Move along the current path
        moveAlongRailPath(delta);

        // Update rotation animation
        updateRotation(delta);
    }

    /**
     * Determines where the enemy should be trying to go based on AI state.
     */
    private Vector3 determineMovementGoal() {
        switch (currentState) {
            case CHASING:
                return player.getPosition();

            case PURSUING:
                // Check if we've reached the last known position
                if (position.dst(lastKnownPlayerPosition) < 2f) {
                    currentState = AIState.WANDERING;
                    wanderUpdateTimer = 0;
                    return null;
                }
                return lastKnownPlayerPosition;

            case WANDERING:
                // Update wander target periodically
                if (wanderUpdateTimer >= GameConfig.WANDER_UPDATE_INTERVAL) {
                    wanderUpdateTimer = 0;
                    updateWanderTargetRail();
                }
                return wanderTarget;

            case PATHFINDING:
                // Use existing path or switch to wandering
                if (currentPath == null || pathIndex >= currentPath.size()) {
                    currentState = AIState.WANDERING;
                    return null;
                }
                return null; // Continue on current path

            default:
                return null;
        }
    }

    /**
     * Checks if we need to recalculate the rail path.
     */
    private boolean shouldRecalculatePath(final Vector3 goal) {
        if (goal == null) {
            return false;
        }

        // No path exists
        if (currentPath == null || currentPath.isEmpty()) {
            return true;
        }

        // Reached end of path
        if (pathIndex >= currentPath.size()) {
            return true;
        }

        // Check if goal has moved significantly from where we were pathfinding to
        final int[] currentGoalGrid = maze.worldToGrid(goal.x, goal.z);
        final int[] pathTargetGrid = maze.worldToGrid(pathTargetPosition.x, pathTargetPosition.z);
        final int gridDistanceMoved = Math.abs(currentGoalGrid[0] - pathTargetGrid[0])
            + Math.abs(currentGoalGrid[1] - pathTargetGrid[1]);

        // For chasing, recalculate if player moved 3+ cells OR after 3 seconds
        if (currentState == AIState.CHASING) {
            if (gridDistanceMoved >= 3) {
                System.out.println("Target moved " + gridDistanceMoved + " cells, recalculating path");
                return true;
            }
            if (pathUpdateTimer >= 3.0f && gridDistanceMoved >= 1) {
                return true;
            }
        }

        // For pursuing, only recalculate if we've completed most of the path
        if (currentState == AIState.PURSUING) {
            // Only recalculate if we're near the end of the path (last 25%)
            if (pathIndex >= currentPath.size() * 0.75) {
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates a new rail path to the goal.
     */
    private void calculateNewRailPath(final Vector3 goal) {
        pathUpdateTimer = 0;
        final int[] startGrid = maze.worldToGrid(position.x, position.z);
        final int[] goalGrid = maze.worldToGrid(goal.x, goal.z);

        // Don't recalculate if we're already at the goal
        if (startGrid[0] == goalGrid[0] && startGrid[1] == goalGrid[1]) {
            return;
        }

        final List<RailPathNode> newPath = PathfindingService.findRailPath(
            railNetwork,
            startGrid[0], startGrid[1],
            goalGrid[0], goalGrid[1],
            currentDirection
        );

        if (newPath != null && !newPath.isEmpty()) {
            // Only reset pathIndex if this is a significantly different path
            // This prevents the enemy from turning around mid-path
            final boolean isNewTarget = currentPath == null
                || currentPath.isEmpty()
                || pathTargetPosition.dst(goal) > 3.0f;

            currentPath = newPath;
            pathTargetPosition.set(goal);

            if (isNewTarget) {
                pathIndex = 0;
            } else {
                // Keep current progress on the path if we're just updating to track target
                pathIndex = Math.min(pathIndex, newPath.size() - 1);
            }
        }
    }

    /**
     * Moves the enemy along the current rail path.
     * Moves toward waypoint positions, uses rail direction for rotation only.
     */
    private void moveAlongRailPath(final float delta) {
        // No valid path
        if (currentPath == null || currentPath.isEmpty() || pathIndex >= currentPath.size()) {
            return;
        }

        // Get current waypoint
        final RailPathNode waypoint = currentPath.get(pathIndex);
        final float waypointX = waypoint.getX() * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
        final float waypointZ = waypoint.getZ() * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;

        // Calculate distance to waypoint
        final float dx = waypointX - position.x;
        final float dz = waypointZ - position.z;
        final float distance = (float) Math.sqrt(dx * dx + dz * dz);

        // Check if we've reached the waypoint
        if (distance < 0.8f) {
            // Move to next waypoint
            pathIndex++;
            return;
        }

        // Update direction to face the waypoint BEFORE moving
        // Only update if we're not currently rotating (to prevent constant direction changes)
        if (rotationProgress >= 0.9f) {
            final RailDirection targetDirection = RailDirection.fromDelta(
                Math.round(dx / distance),
                Math.round(dz / distance)
            );

            if (targetDirection != null && targetDirection != currentDirection) {
                // Calculate rotation cost to decide if we should rotate or just move
                final double rotationCost = currentDirection.rotationCost(targetDirection);

                // Only rotate if it's a significant direction change (90° or 180°)
                if (rotationCost > 1.1) {
                    previousDirection = currentDirection;
                    currentDirection = targetDirection;
                    rotationProgress = 0f;
                }
            }
        }

        // Only move if rotation is mostly complete (at least 30% done)
        // Lower threshold allows smoother movement while still rotating
        if (rotationProgress < 0.3f) {
            return;
        }

        // Move TOWARD the waypoint position (not in rail direction!)
        // The rail direction is only used for visual rotation
        moveDirection.set(dx / distance, 0, dz / distance);
        // Use faster speed when running (chasing)
        final float baseSpeed = isRunning() ? GameConfig.ENEMY_SPEED_RUNNING : GameConfig.ENEMY_SPEED;
        final float speed = baseSpeed * speedMultiplier;
        nextPosition.set(position).mulAdd(moveDirection, speed * delta);

        // Check collision and apply movement
        if (!checkCollision(nextPosition)) {
            // Save last position for stuck detection
            if (stuckTimer == 0) {
                lastPosition.set(position);
            }
            position.set(nextPosition);
        } else {
            // If we hit a wall, we're probably stuck
            handleStuck();
        }

        // Stuck detection
        stuckTimer += delta;
        if (stuckTimer > STUCK_THRESHOLD) {
            final float distanceMoved = position.dst(lastPosition);
            if (distanceMoved < 0.3f) {
                handleStuck();
            }
            stuckTimer = 0;
        }
    }

    /**
     * Updates rotation animation for smooth turning.
     */
    private void updateRotation(final float delta) {
        // Update rotation progress
        if (rotationProgress < 1.0f) {
            rotationProgress = Math.min(1.0f, rotationProgress + delta * 5.0f);
        }

        // Calculate yaw
        if (rotationProgress >= 1.0f) {
            yaw = currentDirection.getYaw();
        } else {
            yaw = interpolateAngle(previousDirection.getYaw(), currentDirection.getYaw(), rotationProgress);
        }
    }

    /**
     * Updates enemy transform (called after movement).
     */
    private void updateSahurTransform() {
        // This is just for internal tracking, actual rendering transform is in MazeRenderer
    }

    /**
     * Interpolates between two angles, taking the shortest path.
     *
     * @param start Starting angle in degrees
     * @param end Ending angle in degrees
     * @param t Interpolation factor (0 to 1)
     * @return Interpolated angle
     */
    private float interpolateAngle(final float start, final float end, final float t) {
        float diff = end - start;

        // Normalize to [-180, 180]
        while (diff > 180f) {
            diff -= 360f;
        }
        while (diff < -180f) {
            diff += 360f;
        }

        float result = start + diff * t;

        // Normalize to [0, 360]
        while (result < 0f) {
            result += 360f;
        }
        while (result >= 360f) {
            result -= 360f;
        }

        return result;
    }

    /**
     * Applies gentle center-pull to keep enemy in middle of corridors.
     * Does NOT snap position - just adjusts movement direction.
     *
     * @param targetDirection Original direction toward target
     * @return Direction with gentle center bias applied
     */
    private Vector3 applyCenterPull(final Vector3 targetDirection) {
        if (targetDirection == null || targetDirection.len() < 0.1f) {
            return targetDirection;
        }

        // Get current grid cell center
        final int[] currentGrid = maze.worldToGrid(position.x, position.z);
        final float[] cellCenter = maze.gridToWorld(currentGrid[0], currentGrid[1]);

        // Calculate offset from center
        final float offsetX = cellCenter[0] - position.x;
        final float offsetZ = cellCenter[1] - position.z;
        final float distFromCenter = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);

        // If far from center, add gentle pull toward center
        if (distFromCenter > 0.8f) {
            final Vector3 centerPull = new Vector3(offsetX, 0, offsetZ).nor();
            // 80% toward target, 20% toward center
            return targetDirection.cpy().nor().scl(0.8f).add(centerPull.scl(0.2f)).nor();
        }

        return targetDirection.nor();
    }

    /**
     * Finds a valid spawn position CLOSE to the player (for testing purposes).
     */
    private void findValidSpawnPosition() {
        final int[] playerGrid = maze.worldToGrid(player.getPosition().x, player.getPosition().z);

        int bestX = -1;
        int bestZ = -1;
        float minDistance = Float.MAX_VALUE;

        // TESTING: Spawn 3-5 cells away from player (not far away)
        final float minTargetDistance = 3f;
        final float maxTargetDistance = 5f;

        for (int z = 0; z < maze.getHeight(); z++) {
            for (int x = 0; x < maze.getWidth(); x++) {
                if (maze.isWall(x, z)) {
                    continue;
                }

                // Check valid neighbors
                int validNeighbors = 0;
                final int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                for (final int[] dir : directions) {
                    final int nx = x + dir[0];
                    final int nz = z + dir[1];
                    if (nx >= 0 && nx < maze.getWidth() && nz >= 0 && nz < maze.getHeight()
                        && !maze.isWall(nx, nz)) {
                        validNeighbors++;
                    }
                }

                if (validNeighbors < 2) {
                    continue;
                }

                final float dx = x - playerGrid[0];
                final float dz = z - playerGrid[1];
                final float distance = (float) Math.sqrt(dx * dx + dz * dz);

                // TESTING: Find closest position within 3-5 cell range
                if (distance >= minTargetDistance && distance <= maxTargetDistance) {
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }
        }

        if (bestX >= 0 && bestZ >= 0) {
            final float[] worldPos = maze.gridToWorld(bestX, bestZ);
            position.set(worldPos[0], GameConfig.ENEMY_HEIGHT, worldPos[1]);
            System.out.println("Enemy spawned at grid (" + bestX + ", " + bestZ + ") - "
                + (int)minDistance + " cells from player [TESTING MODE: CLOSE SPAWN]");
        } else {
            final float[] centerPos = maze.gridToWorld(maze.getWidth() / 2, maze.getHeight() / 2);
            position.set(centerPos[0], GameConfig.ENEMY_HEIGHT, centerPos[1]);
            System.out.println("Enemy spawned at maze center (fallback)");
        }
    }

    /**
     * Updates the wander target to a random valid adjacent cell.
     */
    private void updateWanderTarget() {
        final int[] currentGrid = maze.worldToGrid(position.x, position.z);

        if (currentGrid[0] < 0 || currentGrid[0] >= maze.getWidth()
            || currentGrid[1] < 0 || currentGrid[1] >= maze.getHeight()) {
            final float[] centerPos = maze.gridToWorld(maze.getWidth() / 2, maze.getHeight() / 2);
            wanderTarget.set(centerPos[0], GameConfig.ENEMY_HEIGHT, centerPos[1]);
            return;
        }

        final int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0},
                                     {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        final List<int[]> validDirections = new ArrayList<>();

        for (final int[] dir : directions) {
            final int targetX = currentGrid[0] + dir[0];
            final int targetZ = currentGrid[1] + dir[1];

            if (targetX >= 0 && targetX < maze.getWidth()
                && targetZ >= 0 && targetZ < maze.getHeight()
                && !maze.isWall(targetX, targetZ)) {
                validDirections.add(new int[]{targetX, targetZ});
            }
        }

        if (!validDirections.isEmpty()) {
            final int[] chosen = validDirections.get(random.nextInt(validDirections.size()));
            final float[] worldPos = maze.gridToWorld(chosen[0], chosen[1]);
            wanderTarget.set(worldPos[0], GameConfig.ENEMY_HEIGHT, worldPos[1]);
        } else {
            wanderTarget.set(position);
            System.out.println("Warning: Enemy has no valid wander targets at ("
                + currentGrid[0] + ", " + currentGrid[1] + ")");
        }
    }

    /**
     * Updates the wander target to a random valid adjacent cell using rail directions only.
     * This ensures wandering follows the rail network (no diagonal movement).
     */
    private void updateWanderTargetRail() {
        final int[] currentGrid = maze.worldToGrid(position.x, position.z);

        if (currentGrid[0] < 0 || currentGrid[0] >= maze.getWidth()
            || currentGrid[1] < 0 || currentGrid[1] >= maze.getHeight()) {
            final float[] centerPos = maze.gridToWorld(maze.getWidth() / 2, maze.getHeight() / 2);
            wanderTarget.set(centerPos[0], GameConfig.ENEMY_HEIGHT, centerPos[1]);
            return;
        }

        // Only use orthogonal directions (N, S, E, W) to match rail network
        final int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        final List<int[]> validDirections = new ArrayList<>();

        // Pick a target 3-5 cells away in a random direction
        final int distance = 3 + random.nextInt(3);

        for (final int[] dir : directions) {
            final int targetX = currentGrid[0] + dir[0] * distance;
            final int targetZ = currentGrid[1] + dir[1] * distance;

            if (targetX >= 0 && targetX < maze.getWidth()
                && targetZ >= 0 && targetZ < maze.getHeight()
                && !maze.isWall(targetX, targetZ)) {
                validDirections.add(new int[]{targetX, targetZ});
            }
        }

        if (!validDirections.isEmpty()) {
            final int[] chosen = validDirections.get(random.nextInt(validDirections.size()));
            final float[] worldPos = maze.gridToWorld(chosen[0], chosen[1]);
            wanderTarget.set(worldPos[0], GameConfig.ENEMY_HEIGHT, worldPos[1]);
        } else {
            // Fallback to adjacent cell if no distant targets available
            updateWanderTarget();
        }
    }

    /**
     * Updates the rail-based path to the player's position.
     */
    private void updatePathToPlayer() {
        final int[] enemyGrid = maze.worldToGrid(position.x, position.z);
        final int[] playerGrid = maze.worldToGrid(player.getPosition().x, player.getPosition().z);

        if (enemyGrid[0] != playerGrid[0] || enemyGrid[1] != playerGrid[1]) {
            currentPath = PathfindingService.findRailPath(railNetwork,
                enemyGrid[0], enemyGrid[1], playerGrid[0], playerGrid[1],
                currentDirection);
            pathIndex = 0;
        } else {
            currentState = AIState.WANDERING;
        }
    }

    /**
     * Handles when enemy gets stuck.
     */
    private void handleStuck() {
        System.out.println("Enemy is stuck at (" + position.x + ", " + position.z + "), recovering...");

        // Try to skip to next waypoint if we have a path
        if (currentPath != null && pathIndex < currentPath.size() - 1) {
            pathIndex++;
            System.out.println("Skipping to next waypoint: " + pathIndex);
            return;
        }

        // Otherwise, clear path and pick a new destination
        currentPath = null;
        pathIndex = 0;
        currentState = AIState.WANDERING;
        updateWanderTargetRail();
    }

    /**
     * Checks collision with maze walls using larger radius for enemy.
     */
    private boolean checkCollision(final Vector3 testPosition) {
        final int[] grid = maze.worldToGrid(testPosition.x, testPosition.z);

        // Check wider area due to larger collision radius
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                final int checkX = grid[0] + dx;
                final int checkZ = grid[1] + dz;

                if (maze.isWall(checkX, checkZ)) {
                    final float[] wallWorld = maze.gridToWorld(checkX, checkZ);
                    final float wallMinX = wallWorld[0] - Maze.CELL_SIZE / 2f;
                    final float wallMaxX = wallWorld[0] + Maze.CELL_SIZE / 2f;
                    final float wallMinZ = wallWorld[1] - Maze.CELL_SIZE / 2f;
                    final float wallMaxZ = wallWorld[1] + Maze.CELL_SIZE / 2f;

                    final float closestX = Math.max(wallMinX, Math.min(testPosition.x, wallMaxX));
                    final float closestZ = Math.max(wallMinZ, Math.min(testPosition.z, wallMaxZ));

                    final float dx2 = testPosition.x - closestX;
                    final float dz2 = testPosition.z - closestZ;
                    final float distSquared = dx2 * dx2 + dz2 * dz2;

                    if (distSquared < GameConfig.ENEMY_COLLISION_RADIUS * GameConfig.ENEMY_COLLISION_RADIUS) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Animation name constants for enemy animations.
     * Designed for easy extensibility - add new animations here.
     */
    public static class Animations {
        public static final String WALK = "walk";
        public static final String IDLE = "idle";     // Future: idle animation
        public static final String ATTACK = "attack"; // Future: attack animation
        public static final String RUN = "run";       // Future: separate run animation

        private Animations() {
            // Utility class, no instantiation
        }
    }

    /**
     * Gets the current animation name based on AI state.
     * Currently returns "walk" for all states, but designed for easy extension.
     *
     * @return The animation name to play
     */
    public String getCurrentAnimationName() {
        switch (currentState) {
            case WANDERING:
            case CHASING:
            case PURSUING:
            case PATHFINDING:
                return Animations.WALK;
            default:
                return Animations.WALK;
        }
    }

    /**
     * Gets the animation speed multiplier based on AI state.
     * Used to make animations faster/slower depending on enemy behavior.
     *
     * @return Animation speed multiplier (1.0 = normal speed)
     */
    public float getAnimationSpeedMultiplier() {
        final float adjustedMultiplier = speedMultiplier;
        switch (currentState) {
            case WANDERING:
                return GameConfig.ENEMY_ANIM_SPEED_WANDER * adjustedMultiplier;  // Slower, casual walk
            case CHASING:
                return GameConfig.ENEMY_ANIM_SPEED_CHASE * adjustedMultiplier;   // Faster, running
            case PURSUING:
                return GameConfig.ENEMY_ANIM_SPEED_PURSUE * adjustedMultiplier;  // Medium, determined walk
            case PATHFINDING:
                return GameConfig.ENEMY_ANIM_SPEED_PURSUE * adjustedMultiplier;
            default:
                return 1.0f * adjustedMultiplier;
        }
    }

    /**
     * Checks if the enemy is currently running (chasing state).
     * Used by renderer to switch between walking and running models.
     *
     * @return true if enemy should use running animation/model
     */
    public boolean isRunning() {
        return currentState == AIState.CHASING;
    }

    // Getters
    public Vector3 getPosition() {
        return position;
    }

    public AIState getCurrentState() {
        return currentState;
    }

    public float getYaw() {
        return yaw;
    }

    public float getTimeSincePlayerSeen() {
        return timeSincePlayerSeen;
    }

    public float getCatchRadius() {
        return GameConfig.ENEMY_CATCH_RADIUS;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }

    public RailNetwork getRailNetwork() {
        return railNetwork;
    }

    public void setSpeedMultiplier(final float speedMultiplier) {
        this.speedMultiplier = Math.max(0.05f, speedMultiplier);
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }
}
