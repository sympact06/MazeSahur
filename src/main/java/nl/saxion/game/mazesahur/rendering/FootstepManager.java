package nl.saxion.game.mazesahur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Quaternion;
import nl.saxion.game.mazesahur.entity.Enemy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages footstep decals that appear where the enemy walks.
 * Footsteps are rendered as textured quads on the floor that fade over time.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class FootstepManager {

    /**
     * Represents a single footstep with position, rotation, and lifetime.
     */
    private static class Footstep {
        final Decal decal;
        float age; // Time since creation in seconds
        final float maxAge; // Maximum lifetime before removal

        Footstep(final Decal decal, final float maxAge) {
            this.decal = decal;
            this.age = 0f;
            this.maxAge = maxAge;
        }

        /**
         * Updates the footstep age and applies fade-out effect.
         *
         * @param delta Time since last frame
         * @return true if footstep should be removed (too old)
         */
        boolean update(final float delta) {
            age += delta;

            // Apply fade-out effect based on age
            final float fadeProgress = age / maxAge;
            final float alpha = Math.max(0f, 1f - fadeProgress);

            // Update decal alpha
            decal.setColor(1f, 1f, 1f, alpha * 0.6f); // Max 0.6 alpha for subtle effect

            return age >= maxAge;
        }
    }

    // Footstep configuration
    private static final float FOOTSTEP_INTERVAL = 0.5f; // Place footstep every 0.5 seconds
    private static final float FOOTSTEP_LIFETIME = 20.0f; // Footsteps last 20 seconds
    private static final float FOOTSTEP_SIZE = 0.8f; // Size of footstep decal
    private static final float FOOTSTEP_Y_OFFSET = 0.02f; // Slightly above floor to prevent z-fighting

    // State tracking
    private final List<Footstep> footsteps;
    private float timeSinceLastFootstep;
    private Vector3 lastFootstepPosition;
    private Texture footstepTexture;
    private DecalBatch decalBatch;
    private CameraGroupStrategy cameraGroupStrategy;
    private boolean isLeftFoot; // Track which foot to place next

    /**
     * Creates a new footstep manager.
     */
    public FootstepManager() {
        this.footsteps = new ArrayList<>();
        this.timeSinceLastFootstep = 0f;
        this.lastFootstepPosition = new Vector3();
        this.isLeftFoot = true; // Start with left foot
    }

    /**
     * Initializes the footstep manager and loads textures.
     */
    public void initialize() {
        // Load footstep texture
        try {
            footstepTexture = new Texture(Gdx.files.internal("img/footprint.png"), true);
            footstepTexture.setFilter(
                Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear
            );
            System.out.println("[FootstepManager] Loaded footprint texture");
        } catch (Exception e) {
            System.err.println("[FootstepManager] Failed to load footprint texture: " + e.getMessage());
            e.printStackTrace();
        }

        // Create decal batch for rendering footsteps
        cameraGroupStrategy = new CameraGroupStrategy(null);
        decalBatch = new DecalBatch(cameraGroupStrategy);
        System.out.println("[FootstepManager] Initialized DecalBatch");
    }

    /**
     * Updates footstep system based on enemy movement.
     *
     * @param delta Time since last frame
     * @param enemy The enemy entity
     */
    public void update(final float delta, final Enemy enemy) {
        if (footstepTexture == null) {
            return; // Not initialized
        }

        // Update existing footsteps (fade out and remove old ones)
        final Iterator<Footstep> iterator = footsteps.iterator();
        while (iterator.hasNext()) {
            final Footstep footstep = iterator.next();
            if (footstep.update(delta)) {
                iterator.remove(); // Remove expired footstep
            }
        }

        // Track time since last footstep
        timeSinceLastFootstep += delta;

        // Check if we should place a new footstep
        if (timeSinceLastFootstep >= FOOTSTEP_INTERVAL) {
            final Vector3 enemyPos = enemy.getPosition();

            // Check if enemy has moved enough (at least 0.5 units from last footstep)
            final float distanceMoved = enemyPos.dst(lastFootstepPosition);
            if (distanceMoved >= 0.5f) {
                placeFootstep(enemyPos, enemy.getYaw(), isLeftFoot);
                lastFootstepPosition.set(enemyPos);
                timeSinceLastFootstep = 0f;
                isLeftFoot = !isLeftFoot; // Alternate between left and right foot
            }
        }
    }

    /**
     * Places a new footstep at the given position and rotation.
     *
     * @param position World position for footstep
     * @param yaw Enemy rotation in degrees
     * @param isLeftFoot True for left foot, false for right foot
     */
    private void placeFootstep(final Vector3 position, final float yaw, final boolean isLeftFoot) {
        if (footstepTexture == null) {
            return;
        }

        // Calculate offset perpendicular to movement direction for left/right foot
        final float offsetDistance = 0.15f; // Distance from center (15cm to left or right)
        final float yawRadians = (float) Math.toRadians(yaw);

        // Calculate perpendicular offset (90 degrees from yaw direction)
        // Right vector is perpendicular to forward direction
        final float offsetX = (float) Math.cos(yawRadians) * offsetDistance * (isLeftFoot ? 1 : -1);
        final float offsetZ = (float) Math.sin(yawRadians) * offsetDistance * (isLeftFoot ? 1 : -1);

        // Apply offset to position
        final float footX = position.x + offsetX;
        final float footZ = position.z + offsetZ;

        // Create decal for footstep
        final Decal decal = Decal.newDecal(FOOTSTEP_SIZE, FOOTSTEP_SIZE,
            new com.badlogic.gdx.graphics.g2d.TextureRegion(footstepTexture), true);

        // Position slightly above floor to prevent z-fighting
        decal.setPosition(footX, FOOTSTEP_Y_OFFSET, footZ);

        // Create rotation to lay flat on ground using Quaternion
        // Step 1: Rotate 90 degrees around X axis to lay flat (from XY plane to XZ plane)
        final Quaternion rotationX = new Quaternion(Vector3.X, 90f);

        // Step 2: Rotate around Y axis to match enemy direction
        final Quaternion rotationY = new Quaternion(Vector3.Y, yaw);

        // Step 3: Mirror the footprint for right foot (flip 180 degrees around Z axis)
        final Quaternion rotationZ = isLeftFoot ?
            new Quaternion(Vector3.Z, 0f) : // No flip for left foot
            new Quaternion(Vector3.Z, 180f); // Flip 180 degrees for right foot

        // Combine rotations: first lay flat, then rotate to match direction, then flip if needed
        final Quaternion finalRotation = rotationY.mul(rotationX).mul(rotationZ);
        decal.setRotation(finalRotation);

        // Set initial alpha (subtle)
        decal.setColor(1f, 1f, 1f, 0.6f);

        // Add to list
        footsteps.add(new Footstep(decal, FOOTSTEP_LIFETIME));

    }

    /**
     * Renders all footsteps using the decal batch.
     *
     * @param camera The game camera
     */
    public void render(final PerspectiveCamera camera) {
        if (footstepTexture == null || footsteps.isEmpty()) {
            return;
        }

        // Update camera for decal batch
        if (cameraGroupStrategy != null) {
            cameraGroupStrategy.setCamera(camera);
        }

        // Add all footsteps to batch
        for (final Footstep footstep : footsteps) {
            decalBatch.add(footstep.decal);
        }

        // Render all footsteps
        decalBatch.flush();
    }

    /**
     * Clears all footsteps.
     */
    public void clear() {
        footsteps.clear();
    }

    /**
     * Disposes resources.
     */
    public void dispose() {
        if (footstepTexture != null) {
            footstepTexture.dispose();
        }
        if (decalBatch != null) {
            decalBatch.dispose();
        }
        footsteps.clear();
    }
}