package nl.saxion.game.mazesahur.rendering;

import nl.saxion.game.mazesahur.config.GameConfig;
import com.badlogic.gdx.math.Vector3;

/**
 * Manages lighting with custom shader-based spotlight.
 * Provides realistic flashlight with proper cone attenuation.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class LightingManager {
    private final SpotlightShader shader;
    private boolean flashlightEnabled = true;
    private boolean flashlightSuppressed = false;

    // Flashlight parameters
    private final Vector3 spotPosition = new Vector3();
    private final Vector3 spotDirection = new Vector3(0, 0, -1);
    private float baseIntensity = GameConfig.FLASHLIGHT_INTENSITY;
    private float intensityMultiplier = 1f;
    private final Vector3 defaultFlashlightColor = new Vector3(1.4f, 1.4f, 1.35f);
    private final Vector3 flashlightColor = new Vector3(1.4f, 1.4f, 1.35f);
    private final Vector3 tmpForward = new Vector3();
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();
    private final Vector3 tmpBobDirection = new Vector3();

    // Bobbing effect
    private float bobbingTime = 0f;
    private static final float BOBBING_SPEED = 3.5f;
    private static final float BOBBING_AMOUNT = 0.08f;
    private static final float SIDE_BOBBING_AMOUNT = 0.06f;
    private static final float INTENSITY_FLICKER_AMOUNT = 0.1f;

    /**
     * Creates a new lighting manager with custom shader.
     */
    public LightingManager() {
        shader = new SpotlightShader();
    }

    /**
     * Updates flashlight position and direction with realistic bobbing.
     *
     * @param playerPosition Player's world position
     * @param cameraDirection Camera's forward direction
     * @param delta Time since last frame
     * @param isMoving Whether the player is moving
     */
    public void updateFlashlight(final Vector3 playerPosition, final Vector3 cameraDirection,
                                  final float delta, final boolean isMoving) {
        if (!flashlightEnabled || flashlightSuppressed) {
            shader.setEnabled(false);
            return;
        }

        shader.setEnabled(true);
        shader.setSpotColor(flashlightColor);

        // Update bobbing
        if (isMoving) {
            bobbingTime += delta * BOBBING_SPEED;
        } else {
            bobbingTime += delta * BOBBING_SPEED * 0.3f;
        }

        // Calculate bobbing offsets
        final float verticalBob = (float) Math.sin(bobbingTime) * BOBBING_AMOUNT;
        final float horizontalBob = (float) Math.cos(bobbingTime * 1.3f) * SIDE_BOBBING_AMOUNT;

        // Calculate direction with bobbing
        final Vector3 forward = tmpForward.set(cameraDirection).nor();
        final Vector3 right = tmpRight.set(forward).crs(Vector3.Y).nor();
        final Vector3 up = tmpUp.set(right).crs(forward).nor();
        final Vector3 bobDirection = tmpBobDirection.set(forward)
            .mulAdd(right, horizontalBob)
            .mulAdd(up, verticalBob)
            .nor();

        // Position flashlight at player with slight offset
        spotPosition.set(playerPosition).mulAdd(bobDirection, 0.3f).add(0, -0.15f, 0);
        spotDirection.set(bobDirection);

        // Calculate intensity with flickering
        float intensity = baseIntensity * intensityMultiplier;
        if (isMoving) {
            intensity += (float) Math.sin(bobbingTime * 3.7f) * INTENSITY_FLICKER_AMOUNT;
        } else {
            intensity *= 0.95f;
        }

        shader.updateFlashlight(spotPosition, spotDirection, intensity);
    }

    /**
     * Gets the custom shader for rendering.
     *
     * @return The spotlight shader
     */
    public SpotlightShader getShader() {
        return shader;
    }

    /**
     * Toggles flashlight on/off.
     */
    public void toggleFlashlight() {
        flashlightEnabled = !flashlightEnabled;
    }

    /**
     * Checks if flashlight is enabled.
     *
     * @return True if flashlight is on
     */
    public boolean isFlashlightEnabled() {
        return flashlightEnabled;
    }

    /**
     * Suppresses the flashlight (e.g. power outage) without changing the toggle state.
     */
    public void setFlashlightSuppressed(final boolean suppressed) {
        this.flashlightSuppressed = suppressed;
    }

    /**
     * Sets a temporary intensity multiplier (e.g. dimming).
     */
    public void setIntensityMultiplier(final float multiplier) {
        this.intensityMultiplier = multiplier;
    }

    public void setFlashlightColor(final Vector3 color) {
        this.flashlightColor.set(color);
    }

    public void resetFlashlightColor() {
        this.flashlightColor.set(defaultFlashlightColor);
    }

    /**
     * Disposes shader resources.
     */
    public void dispose() {
        shader.dispose();
    }
}
