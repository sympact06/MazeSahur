package nl.saxion.game.mazesahur.config;

/**
 * Central configuration class containing all game constants.
 * Provides a single source of truth for game parameters.
 * 
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public final class GameConfig {
    
    // Prevent instantiation
    private GameConfig() {
        throw new AssertionError("Cannot instantiate GameConfig");
    }
    
    // Display settings
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;
    public static final float FIELD_OF_VIEW = 67f;
    public static final float CAMERA_NEAR = 0.01f;
    public static final float CAMERA_FAR = 100f;
    
    // Player settings
    public static final float PLAYER_MOVE_SPEED = 12f;
    public static final float PLAYER_HEIGHT = 3f;
    public static final float PLAYER_COLLISION_RADIUS = 2.5f;
    
    // Input settings
    public static final float MOUSE_SENSITIVITY = 0.2f;
    public static final float MAX_PITCH = 89f;
    
    // World settings
    public static final int MAZE_SIZE = 25;
    public static final float CELL_SIZE = 8f;
    public static final float WALL_HEIGHT = 8f;
    
    // Enemy settings
    public static final float ENEMY_SPEED = 4f;
    public static final float ENEMY_COLLISION_RADIUS = 3.5f;
    public static final float ENEMY_CATCH_RADIUS = 2.0f;
    public static final float ENEMY_HEIGHT = 0.5f;
    public static final float ENEMY_SCALE = 0.005f;
    public static final float ENEMY_DETECTION_RANGE = 100f;
    public static final float ENEMY_CHASE_MEMORY_DURATION = 10f;
    public static final float PATH_UPDATE_INTERVAL = 3f;
    public static final float WANDER_UPDATE_INTERVAL = 2f;

    // Death & Jumpscare settings
    public static final float JUMPSCARE_DURATION = 1.5f;
    public static final float JUMPSCARE_SHAKE_INTENSITY = 0.5f;
    public static final float DEATH_SCREEN_FADE_DURATION = 2.0f;
    
    // Lighting settings
    public static final float FLASHLIGHT_INTENSITY = 1.5f;
    public static final float FOG_DENSITY = 0.12f;
    public static final float FOG_START_DISTANCE = 8f;
    public static final float FOG_END_DISTANCE = 35f;

    // VR settings
    // NOTE: VR requires x64 architecture (Intel) - not supported on ARM64 (Apple Silicon)
    public static final boolean VR_ENABLED = true; // Toggle VR mode (set to true on x64 systems with VR)
    public static final boolean VR_SNAP_TURN = false; // true = snap turn, false = smooth rotation
    public static final float VR_SNAP_TURN_ANGLE = 30.0f; // Degrees per snap
    public static final float VR_SMOOTH_TURN_SPEED = 90.0f; // Degrees per second at full thumbstick
    public static final float VR_CONTROLLER_DEADZONE = 0.3f; // Thumbstick deadzone
}

