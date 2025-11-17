package nl.saxion.game.mazesahur.vr;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Manages stereo camera setup for VR rendering.
 * Creates and updates separate cameras for left and right eyes with proper IPD offset.
 * Integrates VR headset tracking for head position and rotation.
 *
 * Camera Hierarchy:
 * - Body position: Player entity position (controlled by movement)
 * - Head position: Body position + VR headset tracking offset
 * - Eye position: Head position ± IPD/2 offset
 *
 * @author Tim (VR Conversion)
 * @version 1.0
 */
public class VRCameraController {

    private final VRManager vrManager;

    private final PerspectiveCamera leftEyeCamera;
    private final PerspectiveCamera rightEyeCamera;

    // Body transform (controlled by player movement)
    private final Vector3 bodyPosition = new Vector3();
    private float bodyYaw = 0.0f; // Body rotation in degrees (Y-axis)

    // VR head tracking offset from body
    private final Vector3 headOffset = new Vector3();
    private final Quaternion headRotation = new Quaternion();

    // Combined transforms
    private final Vector3 leftEyePosition = new Vector3();
    private final Vector3 rightEyePosition = new Vector3();
    private final Vector3 leftEyeDirection = new Vector3();
    private final Vector3 rightEyeDirection = new Vector3();

    /**
     * Creates VR camera controller with stereo camera setup.
     *
     * @param vrManager VR runtime manager
     * @param screenWidth Screen width (used for aspect ratio)
     * @param screenHeight Screen height (used for aspect ratio)
     */
    public VRCameraController(final VRManager vrManager,
                              final int screenWidth,
                              final int screenHeight) {
        this.vrManager = vrManager;

        // Create left eye camera
        leftEyeCamera = new PerspectiveCamera(
            90.0f, // FOV - will be overridden by VR projection
            screenWidth,
            screenHeight
        );
        leftEyeCamera.near = 0.01f;
        leftEyeCamera.far = 100.0f;

        // Create right eye camera
        rightEyeCamera = new PerspectiveCamera(
            90.0f, // FOV - will be overridden by VR projection
            screenWidth,
            screenHeight
        );
        rightEyeCamera.near = 0.01f;
        rightEyeCamera.far = 100.0f;

        System.out.println("[VRCameraController] Stereo cameras initialized with IPD: "
            + (vrManager.getIPD() * 1000.0f) + "mm");
    }

    /**
     * Updates camera positions and orientations from VR tracking and body transform.
     * Should be called every frame before rendering.
     */
    public void update() {
        // Update VR tracking to get latest head position/rotation
        vrManager.updateTracking();

        // Get head tracking data from VR
        headOffset.set(vrManager.getHeadPosition());
        headRotation.set(vrManager.getHeadRotation());

        // Calculate combined head position (body + VR offset rotated by body yaw)
        final Matrix4 bodyRotation = new Matrix4().setToRotation(Vector3.Y, bodyYaw);
        final Vector3 rotatedHeadOffset = headOffset.cpy().mul(bodyRotation);
        final Vector3 finalHeadPosition = bodyPosition.cpy().add(rotatedHeadOffset);

        // Calculate eye positions (head position ± IPD/2 along head's right vector)
        final Vector3 headRight = new Vector3(1, 0, 0).mul(headRotation);
        final float halfIPD = vrManager.getIPD() / 2.0f;

        leftEyePosition.set(finalHeadPosition).sub(headRight.cpy().scl(halfIPD));
        rightEyePosition.set(finalHeadPosition).add(headRight.cpy().scl(halfIPD));

        // Calculate eye directions (forward vector from head rotation + body rotation)
        final Quaternion combinedRotation = new Quaternion().setFromAxis(Vector3.Y, bodyYaw);
        combinedRotation.mul(headRotation);

        final Vector3 forward = new Vector3(0, 0, -1).mul(combinedRotation);
        leftEyeDirection.set(forward);
        rightEyeDirection.set(forward);

        // Update camera transforms
        updateCameraTransforms();
    }

    /**
     * Updates libGDX camera objects with calculated positions and directions.
     */
    private void updateCameraTransforms() {
        // Update left eye camera
        leftEyeCamera.position.set(leftEyePosition);
        leftEyeCamera.direction.set(leftEyeDirection).nor();
        leftEyeCamera.up.set(Vector3.Y); // Assuming upright orientation

        // Override projection matrix with VR runtime's matrix
        leftEyeCamera.projection.set(vrManager.getLeftEyeProjection());
        leftEyeCamera.combined.set(leftEyeCamera.projection);
        Matrix4.mul(leftEyeCamera.combined.val, leftEyeCamera.view.val);
        leftEyeCamera.update();

        // Update right eye camera
        rightEyeCamera.position.set(rightEyePosition);
        rightEyeCamera.direction.set(rightEyeDirection).nor();
        rightEyeCamera.up.set(Vector3.Y);

        // Override projection matrix with VR runtime's matrix
        rightEyeCamera.projection.set(vrManager.getRightEyeProjection());
        rightEyeCamera.combined.set(rightEyeCamera.projection);
        Matrix4.mul(rightEyeCamera.combined.val, rightEyeCamera.view.val);
        rightEyeCamera.update();
    }

    /**
     * Sets body position (player entity position).
     *
     * @param position World position
     */
    public void setBodyPosition(final Vector3 position) {
        this.bodyPosition.set(position);
    }

    /**
     * Sets body yaw rotation (player body facing direction).
     *
     * @param yaw Rotation in degrees (Y-axis)
     */
    public void setBodyYaw(final float yaw) {
        this.bodyYaw = yaw;
    }

    /**
     * Rotates body yaw by delta angle.
     *
     * @param deltaYaw Rotation change in degrees
     */
    public void rotateBody(final float deltaYaw) {
        this.bodyYaw += deltaYaw;
        // Normalize to 0-360
        while (this.bodyYaw < 0) {
            this.bodyYaw += 360.0f;
        }
        while (this.bodyYaw >= 360.0f) {
            this.bodyYaw -= 360.0f;
        }
    }

    /**
     * Gets the forward direction vector for body movement.
     * This is based on body yaw only (ignoring head pitch).
     *
     * @return Normalized forward vector for movement
     */
    public Vector3 getBodyForward() {
        final double yawRad = Math.toRadians(bodyYaw);
        return new Vector3(
            (float) Math.sin(yawRad),
            0.0f,
            -(float) Math.cos(yawRad)
        ).nor();
    }

    /**
     * Gets the right direction vector for body movement.
     *
     * @return Normalized right vector for movement
     */
    public Vector3 getBodyRight() {
        return getBodyForward().crs(Vector3.Y).nor();
    }

    /**
     * Gets the actual head look direction (combining body yaw and VR head rotation).
     * Useful for flashlight direction.
     *
     * @return Normalized head look direction
     */
    public Vector3 getHeadLookDirection() {
        return new Vector3(leftEyeDirection); // Same for both eyes
    }

    /**
     * Gets combined head position in world space.
     *
     * @return Head position (body + VR offset)
     */
    public Vector3 getHeadPosition() {
        return new Vector3(leftEyePosition).add(rightEyePosition).scl(0.5f);
    }

    // Getters for cameras

    /**
     * @return Left eye camera for rendering
     */
    public PerspectiveCamera getLeftEyeCamera() {
        return leftEyeCamera;
    }

    /**
     * @return Right eye camera for rendering
     */
    public PerspectiveCamera getRightEyeCamera() {
        return rightEyeCamera;
    }

    /**
     * @return Current body position
     */
    public Vector3 getBodyPosition() {
        return new Vector3(bodyPosition);
    }

    /**
     * @return Current body yaw in degrees
     */
    public float getBodyYaw() {
        return bodyYaw;
    }

    /**
     * @return Head rotation from VR tracking
     */
    public Quaternion getHeadRotation() {
        return new Quaternion(headRotation);
    }
}
