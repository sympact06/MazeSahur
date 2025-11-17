package nl.saxion.game.mazesahur.vr;

import com.badlogic.gdx.math.Vector2;
import org.lwjgl.openvr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Handles VR controller input for Quest 2 and OpenVR controllers.
 * Maps thumbsticks, buttons, and triggers to game actions.
 *
 * Button Mapping:
 * - Left Thumbstick: Movement (WASD replacement)
 * - Right Thumbstick: Body rotation (snap or smooth)
 * - Right Trigger/A Button: Toggle flashlight
 * - Left Grip/X Button: Interact with elevator
 *
 * @author Tim (VR Conversion)
 * @version 1.0
 */
public class VRInput {

    // Controller device indices (found during tracking)
    private int leftControllerIndex = -1;
    private int rightControllerIndex = -1;

    // Movement thumbstick (left controller)
    private final Vector2 movementStick = new Vector2();

    // Rotation thumbstick (right controller)
    private final Vector2 rotationStick = new Vector2();

    // Button states (for edge detection - just pressed/released)
    private boolean flashlightButtonPressed = false;
    private boolean flashlightButtonPreviouslyPressed = false;

    private boolean interactButtonPressed = false;
    private boolean interactButtonPreviouslyPressed = false;

    // Rotation settings
    private boolean useSnapTurn = false; // false = smooth rotation
    private float snapTurnAngle = 30.0f; // degrees per snap
    private float snapTurnCooldown = 0.0f;
    private static final float SNAP_TURN_COOLDOWN_TIME = 0.3f; // seconds

    /**
     * Updates controller input state.
     * Should be called every frame before processing input.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void update(final float deltaTime) {
        // Update snap turn cooldown
        if (snapTurnCooldown > 0) {
            snapTurnCooldown -= deltaTime;
        }

        // Find controller indices if not found yet
        if (leftControllerIndex == -1 || rightControllerIndex == -1) {
            findControllers();
        }

        // Update controller states
        updateControllerInput();
    }

    /**
     * Finds VR controllers in tracked device list.
     */
    private void findControllers() {
        for (int i = 0; i < VR.k_unMaxTrackedDeviceCount; i++) {
            int deviceClass = VRSystem.VRSystem_GetTrackedDeviceClass(i);

            if (deviceClass == VR.ETrackedDeviceClass_TrackedDeviceClass_Controller) {
                // Determine if left or right controller
                int role = VRSystem.VRSystem_GetControllerRoleForTrackedDeviceIndex(i);

                if (role == VR.ETrackedControllerRole_TrackedControllerRole_LeftHand) {
                    leftControllerIndex = i;
                    System.out.println("[VRInput] Found left controller at index " + i);
                } else if (role == VR.ETrackedControllerRole_TrackedControllerRole_RightHand) {
                    rightControllerIndex = i;
                    System.out.println("[VRInput] Found right controller at index " + i);
                }
            }
        }
    }

    /**
     * Updates button and axis states from controllers.
     */
    private void updateControllerInput() {
        // Store previous button states for edge detection
        flashlightButtonPreviouslyPressed = flashlightButtonPressed;
        interactButtonPreviouslyPressed = interactButtonPressed;

        // Reset current states
        movementStick.set(0, 0);
        rotationStick.set(0, 0);
        flashlightButtonPressed = false;
        interactButtonPressed = false;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VRControllerState controllerState = VRControllerState.malloc(stack);

            // Left Controller - Movement and interaction
            if (leftControllerIndex != -1) {
                if (VRSystem.VRSystem_GetControllerState(leftControllerIndex, controllerState)) {
                    // Get thumbstick axis (typically axis 0 for OpenVR)
                    // rAxis(0) returns a struct with x() and y() methods
                    movementStick.set(controllerState.rAxis(0).x(), controllerState.rAxis(0).y());

                    // Check grip button for interact (button 2)
                    long buttonPressed = controllerState.ulButtonPressed();
                    if ((buttonPressed & (1L << VR.EVRButtonId_k_EButton_Grip)) != 0) {
                        interactButtonPressed = true;
                    }
                }
            }

            // Right Controller - Rotation and flashlight
            if (rightControllerIndex != -1) {
                if (VRSystem.VRSystem_GetControllerState(rightControllerIndex, controllerState)) {
                    // Get thumbstick axis (typically axis 0 for OpenVR)
                    rotationStick.set(controllerState.rAxis(0).x(), controllerState.rAxis(0).y());

                    // Check A button or trigger for flashlight
                    long buttonPressed = controllerState.ulButtonPressed();
                    if ((buttonPressed & (1L << VR.EVRButtonId_k_EButton_A)) != 0
                        || (buttonPressed & (1L << VR.EVRButtonId_k_EButton_SteamVR_Trigger)) != 0) {
                        flashlightButtonPressed = true;
                    }
                }
            }
        }
    }

    /**
     * Gets movement vector from left thumbstick.
     * X component: -1 (left) to +1 (right)
     * Y component: -1 (down/back) to +1 (up/forward)
     *
     * @return Movement thumbstick vector
     */
    public Vector2 getMovementInput() {
        return new Vector2(movementStick);
    }

    /**
     * Gets rotation input from right thumbstick.
     * For snap turn: only triggers when threshold exceeded and cooldown expired.
     * For smooth turn: continuous rotation value.
     *
     * @return Rotation angle to apply this frame (degrees)
     */
    public float getRotationInput() {
        final float deadzone = 0.3f; // Thumbstick deadzone

        if (Math.abs(rotationStick.x) < deadzone) {
            return 0.0f;
        }

        if (useSnapTurn) {
            // Snap turning - only trigger on threshold and cooldown
            if (snapTurnCooldown <= 0) {
                if (rotationStick.x > 0.5f) {
                    snapTurnCooldown = SNAP_TURN_COOLDOWN_TIME;
                    return snapTurnAngle; // Turn right
                } else if (rotationStick.x < -0.5f) {
                    snapTurnCooldown = SNAP_TURN_COOLDOWN_TIME;
                    return -snapTurnAngle; // Turn left
                }
            }
            return 0.0f;
        } else {
            // Smooth turning - continuous rotation
            final float rotationSpeed = 90.0f; // degrees per second at full stick
            return rotationStick.x * rotationSpeed;
        }
    }

    /**
     * Checks if flashlight button was just pressed this frame (rising edge).
     *
     * @return true if button just pressed (not held)
     */
    public boolean isFlashlightButtonJustPressed() {
        return flashlightButtonPressed && !flashlightButtonPreviouslyPressed;
    }

    /**
     * Checks if interact button was just pressed this frame (rising edge).
     *
     * @return true if button just pressed (not held)
     */
    public boolean isInteractButtonJustPressed() {
        return interactButtonPressed && !interactButtonPreviouslyPressed;
    }

    /**
     * Triggers haptic feedback on specified controller.
     *
     * @param hand Which hand to vibrate (0 = left, 1 = right)
     * @param durationMicroseconds Duration in microseconds (1000 = 1ms)
     */
    public void triggerHaptic(final int hand, final int durationMicroseconds) {
        int controllerIndex = (hand == 0) ? leftControllerIndex : rightControllerIndex;

        if (controllerIndex == -1) {
            return;
        }

        // Trigger haptic pulse on axis 0 (standard haptic axis)
        VRSystem.VRSystem_TriggerHapticPulse(controllerIndex, 0, (short) durationMicroseconds);
    }

    /**
     * Triggers short haptic feedback on left controller.
     */
    public void hapticLeft() {
        triggerHaptic(0, 1000); // 1ms pulse
    }

    /**
     * Triggers short haptic feedback on right controller.
     */
    public void hapticRight() {
        triggerHaptic(1, 1000); // 1ms pulse
    }

    // Configuration methods

    /**
     * Sets whether to use snap turning or smooth turning.
     *
     * @param snap true for snap turn, false for smooth
     */
    public void setUseSnapTurn(final boolean snap) {
        this.useSnapTurn = snap;
    }

    /**
     * Sets snap turn angle in degrees.
     *
     * @param angle Degrees to rotate per snap (typically 15-45)
     */
    public void setSnapTurnAngle(final float angle) {
        this.snapTurnAngle = angle;
    }

    /**
     * @return true if using snap turn, false if using smooth turn
     */
    public boolean isUsingSnapTurn() {
        return useSnapTurn;
    }

    /**
     * @return Current snap turn angle in degrees
     */
    public float getSnapTurnAngle() {
        return snapTurnAngle;
    }
}
