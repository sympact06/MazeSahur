package nl.saxion.game.mazesahur.entity;

import com.badlogic.gdx.math.Vector3;
import nl.saxion.game.mazesahur.world.Maze;

/**
 * Represents a police pinboard mounted on a wall.
 * Contains investigation information and clues.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class PolicePinboard {

    /**
     * Wall direction enum for pinboard placement.
     */
    public enum WallFace {
        NORTH(0),   // Facing south (on north wall)
        EAST(90),   // Facing west (on east wall)
        SOUTH(180), // Facing north (on south wall)
        WEST(270);  // Facing east (on west wall)

        private final float rotationDegrees;

        WallFace(final float rotationDegrees) {
            this.rotationDegrees = rotationDegrees;
        }

        public float getRotationDegrees() {
            return rotationDegrees;
        }
    }

    private final Vector3 position;
    private final Maze maze;
    private final WallFace wallFace;

    // Pinboard dimensions
    private static final float PINBOARD_WIDTH = 1.2f;
    private static final float PINBOARD_HEIGHT = 1.0f;
    private static final float PINBOARD_DEPTH = 0.05f;
    private static final float WALL_HEIGHT = 1.5f; // Height on wall (eye level)

    /**
     * Creates a new police pinboard entity.
     *
     * @param maze The game maze
     * @param x X position in world coordinates
     * @param z Z position in world coordinates
     * @param wallFace Direction the pinboard is facing
     */
    public PolicePinboard(final Maze maze, final float x, final float z, final WallFace wallFace) {
        this.maze = maze;
        this.position = new Vector3(x, WALL_HEIGHT, z);
        this.wallFace = wallFace;
    }

    /**
     * Gets the pinboard's position in world coordinates.
     *
     * @return Position vector
     */
    public Vector3 getPosition() {
        return position;
    }

    /**
     * Gets the wall face direction of the pinboard.
     *
     * @return Wall face enum
     */
    public WallFace getWallFace() {
        return wallFace;
    }

    /**
     * Gets the pinboard width.
     *
     * @return Pinboard width
     */
    public float getWidth() {
        return PINBOARD_WIDTH;
    }

    /**
     * Gets the pinboard height.
     *
     * @return Pinboard height
     */
    public float getHeight() {
        return PINBOARD_HEIGHT;
    }

    /**
     * Gets the pinboard depth.
     *
     * @return Pinboard depth
     */
    public float getDepth() {
        return PINBOARD_DEPTH;
    }
}

