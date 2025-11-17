package nl.saxion.game.mazesahur.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import nl.saxion.game.mazesahur.config.GameConfig;
import nl.saxion.game.mazesahur.entity.Player;
import nl.saxion.game.mazesahur.entity.Enemy;
import nl.saxion.game.mazesahur.entity.Elevator;
import nl.saxion.game.mazesahur.rendering.LightingManager;
import nl.saxion.game.mazesahur.rendering.MaterialManager;
import nl.saxion.game.mazesahur.rendering.MazeRenderer;
import nl.saxion.game.mazesahur.world.Maze;
import nl.saxion.game.mazesahur.ui.GameUI;
import nl.saxion.game.mazesahur.vr.VRManager;
import nl.saxion.game.mazesahur.vr.VRInput;
import nl.saxion.game.mazesahur.vr.VRCameraController;
import nl.saxion.game.mazesahur.vr.StereoFramebufferManager;
import nl.saxion.gameapp.GameApp;
import nl.saxion.gameapp.screens.ScalableGameScreen;

/**
 * Main game screen for the 3D horror maze game.
 * Handles rendering, player input, enemy AI, and game logic.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class GameScreen extends ScalableGameScreen {

    // Core components
    private PerspectiveCamera camera;
    private final Player player;
    private final Enemy enemy;
    private final Maze maze;
    private Elevator elevator;

    // Rendering systems
    private LightingManager lightingManager;
    private MaterialManager materialManager;
    private MazeRenderer mazeRenderer;

    // UI
    private GameUI gameUI;

    // VR components (only used when VR_ENABLED = true)
    private VRManager vrManager;
    private VRInput vrInput;
    private VRCameraController vrCameraController;
    private StereoFramebufferManager stereoFramebufferManager;
    private boolean vrMode = false; // Actual VR mode status (depends on hardware availability)

    // Camera control (non-VR mode)
    private float yaw;
    private float pitch;
    private int lastMouseX;
    private int lastMouseY;
    private boolean firstMouse;

    // Body yaw for VR mode (separate from head rotation)
    private float bodyYaw = 0f;

    private static final float MOUSE_SENSITIVITY = 0.2f;
    private static final float MAX_PITCH = 89f;

    // Initialization flag to prevent double-loading
    private boolean initialized = false;

    // Death and jumpscare state
    private boolean isDead = false;
    private float jumpscareTimer = 0f;
    private float survivalTime = 0f;
    private Vector3 jumpscareShakeOffset = new Vector3();
    private boolean jumpscareActive = false;

    /**
     * Creates a new game screen with default settings.
     */
    public GameScreen() {
        super(1280, 720);

        // Initialize world
        maze = new Maze(25, 25);
        maze.generate();

        // Initialize entities
        player = new Player(new Vector3(12f, 3f, 12f));
        enemy = new Enemy(maze, player);

        // Initialize elevator in a valid open position
        elevator = createElevatorInOpenSpace();

        // Camera control initialization
        yaw = 0;
        pitch = 0;
        firstMouse = true;
    }

    @Override
    public void show() {
        // Prevent double-initialization (happens when switching from splash)
        if (initialized) {
            System.out.println("[GameScreen] Already initialized, updating viewport...");

            // Update camera viewport for new window size (900x500 -> 1280x720)
            final int screenWidth = Gdx.graphics.getBackBufferWidth();
            final int screenHeight = Gdx.graphics.getBackBufferHeight();
            if (camera != null) {
                camera.viewportWidth = screenWidth;
                camera.viewportHeight = screenHeight;
                camera.update();
            }

            // Recapture cursor
            Gdx.input.setCursorCatched(true);
            return;
        }

        System.out.println("[GameScreen] Initializing for the first time...");
        initialized = true;

        // Initialize camera (must be done after OpenGL context is ready)
        final int screenWidth = Gdx.graphics.getBackBufferWidth();
        final int screenHeight = Gdx.graphics.getBackBufferHeight();

        // Initialize VR if enabled
        if (GameConfig.VR_ENABLED) {
            System.out.println("[GameScreen] VR mode enabled, initializing VR system...");
            try {
                vrManager = new VRManager();
                if (vrManager.initialize()) {
                    vrMode = true;
                    vrInput = new VRInput();
                    vrInput.setUseSnapTurn(GameConfig.VR_SNAP_TURN);
                    vrInput.setSnapTurnAngle(GameConfig.VR_SNAP_TURN_ANGLE);

                    vrCameraController = new VRCameraController(vrManager, screenWidth, screenHeight);
                    vrCameraController.setBodyPosition(player.getPosition());
                    vrCameraController.setBodyYaw(bodyYaw);

                    stereoFramebufferManager = new StereoFramebufferManager(vrManager);

                    System.out.println("[GameScreen] VR system initialized successfully");
                } else {
                    System.err.println("[GameScreen] VR initialization failed, falling back to desktop mode");
                    vrMode = false;
                }
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[GameScreen] VR native library not available: " + e.getMessage());
                System.err.println("[GameScreen] This is expected on ARM64 macOS (Apple Silicon)");
                System.err.println("[GameScreen] Falling back to desktop mode");
                vrMode = false;
                vrManager = null;
            } catch (Exception e) {
                System.err.println("[GameScreen] Unexpected error during VR initialization: " + e.getMessage());
                e.printStackTrace();
                vrMode = false;
                vrManager = null;
            }
        }

        // Initialize standard camera for non-VR mode or VR fallback
        if (!vrMode) {
            camera = new PerspectiveCamera(67, screenWidth, screenHeight);
            camera.near = 0.01f;
            camera.far = 100f;
        }

        // Initialize rendering systems (requires OpenGL context)
        lightingManager = new LightingManager();
        materialManager = new MaterialManager();
        mazeRenderer = new MazeRenderer(maze, materialManager, lightingManager);

        // Initialize UI
        gameUI = new GameUI();
        gameUI.initialize();

        // Capture cursor for FPS controls (only in non-VR mode)
        if (!vrMode) {
            Gdx.input.setCursorCatched(true);

            // Update camera for desktop mode
            camera.position.set(player.getPosition());
            camera.lookAt(player.getPosition().x, player.getPosition().y, player.getPosition().z - 1);
            camera.update();
        }

        // Initialize rendering systems
        materialManager.loadTextures();
        mazeRenderer.initialize();

        // Initialize enemy position
        enemy.initialize();

        System.out.println("[GameScreen] Initialization complete!");
    }

    @Override
    public void render(final float delta) {
        // Handle jumpscare sequence
        if (jumpscareActive) {
            handleJumpscare(delta);
            return;
        }

        // Check for death condition
        if (!isDead) {
            survivalTime += delta;
            // TEMPORARY: Death disabled for testing
            // checkDeathCondition();
        }

        // Skip normal game logic if dead
        if (!isDead) {
            // Update game state
            handleInput(delta);
            player.update(delta, maze);
            enemy.update(delta);
            elevator.update(delta, player.getPosition()); // Update elevator with player position
            updateCamera();

            // Update lighting
            final boolean isMoving = player.isMoving();
            if (vrMode) {
                // VR mode - use head look direction for flashlight
                final Vector3 headDirection = vrCameraController.getHeadLookDirection();
                final Vector3 headPos = vrCameraController.getHeadPosition();
                lightingManager.updateFlashlight(headPos, headDirection, delta, isMoving);
            } else {
                // Desktop mode - use camera direction
                lightingManager.updateFlashlight(player.getPosition(), camera.direction, delta, isMoving);
            }
            mazeRenderer.updateLampFlicker(delta);

            // Handle input
            handleGameInput();
        }

        if (vrMode) {
            // VR Mode: Stereo rendering to framebuffers

            // Render LEFT eye
            stereoFramebufferManager.bind(StereoFramebufferManager.Eye.LEFT);
            mazeRenderer.renderWithElevator(vrCameraController.getLeftEyeCamera(), elevator);
            mazeRenderer.renderEnemy(vrCameraController.getLeftEyeCamera(), enemy);
            stereoFramebufferManager.unbind();

            // Render RIGHT eye
            stereoFramebufferManager.bind(StereoFramebufferManager.Eye.RIGHT);
            mazeRenderer.renderWithElevator(vrCameraController.getRightEyeCamera(), elevator);
            mazeRenderer.renderEnemy(vrCameraController.getRightEyeCamera(), enemy);
            stereoFramebufferManager.unbind();

            // Submit both eyes to VR compositor
            stereoFramebufferManager.submit();

            // Optional: Render to screen for debugging (side-by-side)
            stereoFramebufferManager.renderToScreen();

            // Note: VR UI rendering would go here (3D world-space UI)
            // TODO: Implement VR UI rendering

        } else {
            // Desktop Mode: Standard mono rendering
            // Clear screen
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            // Render 3D scene (elevator is rendered together with maze to prevent material bleeding)
            mazeRenderer.renderWithElevator(camera, elevator);
            mazeRenderer.renderEnemy(camera, enemy);

            // Render UI (hide during jumpscare)
            if (!jumpscareActive) {
                gameUI.render(this, player, enemy, elevator, lightingManager);
            }
        }
    }

    /**
     * Handles mouse look and WASD movement input (desktop) or VR controller input (VR mode).
     */
    private void handleInput(final float delta) {
        if (vrMode) {
            // VR Mode - Use controller input
            vrInput.update(delta);

            // Get movement from left thumbstick
            final Vector2 movementInput = vrInput.getMovementInput();

            // Get rotation from right thumbstick
            final float rotationDelta = vrInput.getRotationInput();

            // Apply rotation to body yaw
            if (GameConfig.VR_SNAP_TURN) {
                // Snap turning - already returns discrete angles
                bodyYaw += rotationDelta;
            } else {
                // Smooth turning - scale by delta time
                bodyYaw += rotationDelta * delta;
            }

            // Calculate movement direction based on body forward and right vectors
            final Vector3 forward = vrCameraController.getBodyForward();
            final Vector3 right = vrCameraController.getBodyRight();
            final Vector3 moveDirection = new Vector3();

            // Apply thumbstick input to movement direction
            moveDirection.add(forward.cpy().scl(movementInput.y)); // Forward/backward
            moveDirection.add(right.cpy().scl(movementInput.x));   // Left/right

            // Apply movement with collision detection (same as desktop)
            if (moveDirection.len() > 0) {
                moveDirection.nor().scl(GameConfig.PLAYER_MOVE_SPEED * delta);

                // Try full movement first
                final Vector3 newPosition = player.getPosition().cpy().add(moveDirection);

                if (!checkCollision(newPosition)) {
                    // No collision, move freely
                    player.getPosition().set(newPosition);
                } else {
                    // Collision detected - try wall sliding
                    handleWallSliding(moveDirection, delta);
                }
            }

        } else {
            // Desktop Mode - Mouse and keyboard input
            // Mouse look
            final int mouseX = Gdx.input.getX();
            final int mouseY = Gdx.input.getY();

            if (firstMouse) {
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                firstMouse = false;
            }

            final float deltaX = (mouseX - lastMouseX) * MOUSE_SENSITIVITY;
            final float deltaY = (mouseY - lastMouseY) * MOUSE_SENSITIVITY;

            lastMouseX = mouseX;
            lastMouseY = mouseY;

            yaw += deltaX;
            pitch -= deltaY;

            // Clamp pitch
            if (pitch > MAX_PITCH) {
                pitch = MAX_PITCH;
            }
            if (pitch < -MAX_PITCH) {
                pitch = -MAX_PITCH;
            }

            // Calculate movement direction
            final Vector3 forward = getForwardVector();
            final Vector3 right = getRightVector();
            final Vector3 moveDirection = new Vector3();

            if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                moveDirection.add(forward);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                moveDirection.sub(forward);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                moveDirection.sub(right);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                moveDirection.add(right);
            }

            // Apply movement with collision detection
            if (moveDirection.len() > 0) {
                moveDirection.nor().scl(GameConfig.PLAYER_MOVE_SPEED * delta);

                // Try full movement first
                final Vector3 newPosition = player.getPosition().cpy().add(moveDirection);

                if (!checkCollision(newPosition)) {
                    // No collision, move freely
                    player.getPosition().set(newPosition);
                } else {
                    // Try sliding along walls (X direction only)
                    final Vector3 slideX = player.getPosition().cpy().add(moveDirection.x, 0, 0);
                    if (!checkCollision(slideX)) {
                        player.getPosition().set(slideX);
                    } else {
                        // Try sliding along walls (Z direction only)
                        final Vector3 slideZ = player.getPosition().cpy().add(0, 0, moveDirection.z);
                        if (!checkCollision(slideZ)) {
                            player.getPosition().set(slideZ);
                        }
                        // If both fail, player is stuck in corner and doesn't move
                    }
                }
            }
        }
    }

    /**
     * Creates an elevator in a guaranteed open space in the maze.
     * Ensures the elevator doesn't spawn in walls.
     * FOR TESTING: Spawns VERY CLOSE to player spawn position.
     */
    private Elevator createElevatorInOpenSpace() {
        // Player spawns at (12, 3, 12) world coordinates
        // Place elevator integrated into wall - 3 cells in front
        final float elevatorX = 12f; // Same X as player
        final float elevatorZ = 12f + (3 * Maze.CELL_SIZE); // 3 cells in front (+Z)

        // Convert to grid coordinates
        final int[] gridPos = maze.worldToGrid(elevatorX, elevatorZ);
        final int gridX = gridPos[0];
        final int gridZ = gridPos[1];

        // Create opening in maze walls for elevator (2x2 cells to ensure enough space)
        maze.createOpening(gridX, gridZ, 2, 2);

        System.out.println("[GameScreen] ===== ELEVATOR SPAWN DEBUG (INTEGRATED INTO WALL) =====");
        System.out.println("[GameScreen] Player spawn: (12, 3, 12)");
        System.out.println("[GameScreen] Elevator world pos: (" + elevatorX + ", 0, " + elevatorZ + ")");
        System.out.println("[GameScreen] Elevator grid pos: (" + gridX + ", " + gridZ + ")");
        System.out.println("[GameScreen] Created 2x2 opening in maze walls");
        System.out.println("[GameScreen] Distance from player: " + (3 * Maze.CELL_SIZE) + " units (3 cells)");
        System.out.println("[GameScreen] ==================================================");

        return new Elevator(maze, elevatorX, elevatorZ);

        /* OLD CODE - Disabled for testing
        // Convert to grid coordinates
        final int playerGridX = (int) (12f / Maze.CELL_SIZE);
        final int playerGridZ = (int) (12f / Maze.CELL_SIZE);

        // Try positions around player spawn (spiral pattern)
        final int[][] offsets = {
            {0, 4}, {0, 5}, {0, 6},  // In front of player
            {4, 0}, {5, 0}, {6, 0},  // To the right
            {-4, 0}, {-5, 0}, {-6, 0}, // To the left
            {0, -4}, {0, -5}, {0, -6}, // Behind player
            {4, 4}, {-4, 4}, {4, -4}, {-4, -4}, // Diagonals
            {3, 3}, {-3, 3}, {3, -3}, {-3, -3}  // Closer diagonals
        };

        */ // End of old code comment

        /* ORIGINAL SEARCH CODE - Commented out for testing
        for (final int[] offset : offsets) {
            final int x = playerGridX + offset[0];
            final int z = playerGridZ + offset[1];

            // Check bounds
            if (x < 2 || x >= maze.getWidth() - 2 || z < 2 || z >= maze.getHeight() - 2) {
                continue;
            }

            // Check if this position and surrounding area is open (5x5 grid for extra safety)
            boolean isAreaOpen = true;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    final int checkX = x + dx;
                    final int checkZ = z + dz;
                    // Check bounds
                    if (checkX < 0 || checkX >= maze.getWidth() || checkZ < 0 || checkZ >= maze.getHeight()) {
                        isAreaOpen = false;
                        break;
                    }
                    if (maze.isWall(checkX, checkZ)) {
                        isAreaOpen = false;
                        break;
                    }
                }
                if (!isAreaOpen) break;
            }

            // CRITICAL: Check extra space in all 4 directions for the door (door can face any direction)
            // The door extends 2-3 cells from the elevator center
            boolean doorSpaceClear = true;
            if (isAreaOpen) {
                // Check North (-Z direction) - 3 extra cells
                for (int extraZ = -3; extraZ <= -3; extraZ--) {
                    final int checkZ = z + extraZ;
                    if (checkZ < 0 || checkZ >= maze.getHeight() || maze.isWall(x, checkZ)) {
                        doorSpaceClear = false;
                        break;
                    }
                }

                // Check South (+Z direction) - 3 extra cells
                if (doorSpaceClear) {
                    for (int extraZ = 3; extraZ <= 3; extraZ++) {
                        final int checkZ = z + extraZ;
                        if (checkZ >= maze.getHeight() || maze.isWall(x, checkZ)) {
                            doorSpaceClear = false;
                            break;
                        }
                    }
                }

                // Check East (+X direction) - 3 extra cells
                if (doorSpaceClear) {
                    for (int extraX = 3; extraX <= 3; extraX++) {
                        final int checkX = x + extraX;
                        if (checkX >= maze.getWidth() || maze.isWall(checkX, z)) {
                            doorSpaceClear = false;
                            break;
                        }
                    }
                }

                // Check West (-X direction) - 3 extra cells
                if (doorSpaceClear) {
                    for (int extraX = -3; extraX <= -3; extraX--) {
                        final int checkX = x + extraX;
                        if (checkX < 0 || maze.isWall(checkX, z)) {
                            doorSpaceClear = false;
                            break;
                        }
                    }
                }
            }

            // If we found a good spot with door space clear, place elevator here
            if (isAreaOpen && doorSpaceClear) {
                final float elevatorX = x * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
                // Offset elevator backwards (towards -Z) so door has more clearance in front
                final float elevatorZ = z * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f - 2.0f; // 2 units back
                System.out.println("[GameScreen] ===== ELEVATOR SPAWN DEBUG =====");
                System.out.println("[GameScreen] Elevator spawned near player at grid (" + x + ", " + z + ")");
                System.out.println("[GameScreen] World position: (" + elevatorX + ", " + elevatorZ + ") - offset back 2 units");
                System.out.println("[GameScreen] 5x5 area checked + extra door clearance in all 4 directions");
                System.out.println("[GameScreen] ================================");
                return new Elevator(maze, elevatorX, elevatorZ);
            }
        }

        // Fallback: search entire maze for a 5x5 open area with door clearance
        System.out.println("[GameScreen] No suitable spot near player, searching entire maze...");
        for (int z = 4; z < maze.getHeight() - 4; z++) {
            for (int x = 4; x < maze.getWidth() - 4; x++) {
                // Check 5x5 area
                boolean isAreaOpen = true;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (maze.isWall(x + dx, z + dz)) {
                            isAreaOpen = false;
                            break;
                        }
                    }
                    if (!isAreaOpen) break;
                }

                // Check extra door space in all 4 directions
                boolean doorSpaceClear = true;
                if (isAreaOpen) {
                    // North
                    if (maze.isWall(x, z - 3)) doorSpaceClear = false;
                    // South
                    if (doorSpaceClear && maze.isWall(x, z + 3)) doorSpaceClear = false;
                    // East
                    if (doorSpaceClear && maze.isWall(x + 3, z)) doorSpaceClear = false;
                    // West
                    if (doorSpaceClear && maze.isWall(x - 3, z)) doorSpaceClear = false;
                }

                if (isAreaOpen && doorSpaceClear) {
                    final float elevatorX = x * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
                    // Offset elevator backwards (towards -Z) so door has more clearance in front
                    final float elevatorZ = z * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f - 2.0f; // 2 units back
                    System.out.println("[GameScreen] ===== ELEVATOR SPAWN DEBUG =====");
                    System.out.println("[GameScreen] Elevator spawned at grid (" + x + ", " + z + ")");
                    System.out.println("[GameScreen] World position: (" + elevatorX + ", " + elevatorZ + ") - offset back 2 units");
                    System.out.println("[GameScreen] 5x5 area checked + door clearance (fallback search)");
                    System.out.println("[GameScreen] ================================");
                    return new Elevator(maze, elevatorX, elevatorZ);
                }
            }
        }

        // Last resort fallback
        final float fallbackX = (maze.getWidth() / 2) * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
        final float fallbackZ = (maze.getHeight() / 2) * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f - 2.0f; // Offset back
        System.out.println("[GameScreen] Elevator spawned at center (last resort fallback) - offset back 2 units");
        return new Elevator(maze, fallbackX, fallbackZ);
        */ // End of commented fallback code
    }

    /**
     * Checks collision with maze walls and elevator using circular collision detection.
     */
    private boolean checkCollision(final Vector3 position) {
        final int gridX = (int) Math.floor(position.x / Maze.CELL_SIZE);
        final int gridZ = (int) Math.floor(position.z / Maze.CELL_SIZE);

        // Check 3x3 grid around player for walls
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int checkX = gridX + dx;
                final int checkZ = gridZ + dz;

                if (checkX >= 0 && checkX < maze.getWidth()
                    && checkZ >= 0 && checkZ < maze.getHeight()
                    && maze.isWall(checkX, checkZ)) {

                    // Wall bounds in world space
                    final float wallMinX = checkX * Maze.CELL_SIZE;
                    final float wallMaxX = wallMinX + Maze.CELL_SIZE;
                    final float wallMinZ = checkZ * Maze.CELL_SIZE;
                    final float wallMaxZ = wallMinZ + Maze.CELL_SIZE;

                    // Find closest point on wall box to player circle center
                    final float closestX = Math.max(wallMinX, Math.min(position.x, wallMaxX));
                    final float closestZ = Math.max(wallMinZ, Math.min(position.z, wallMaxZ));

                    // Calculate distance from player to closest point
                    final float dx2 = position.x - closestX;
                    final float dz2 = position.z - closestZ;
                    final float distSquared = dx2 * dx2 + dz2 * dz2;

                    // Collision if distance is less than collision radius
                    if (distSquared < GameConfig.PLAYER_COLLISION_RADIUS * GameConfig.PLAYER_COLLISION_RADIUS) {
                        return true;
                    }
                }
            }
        }

        // Check collision with elevator (only blocks if doors are closed)
        if (elevator.blocksPosition(position)) {
            return true;
        }

        return false;
    }

    /**
     * Handles non-movement game input (flashlight toggle, exit, elevator control, etc.).
     */
    private void handleGameInput() {
        if (vrMode) {
            // VR Mode - Use controller buttons

            // Toggle flashlight with A button (right controller)
            if (vrInput.isFlashlightButtonJustPressed()) {
                lightingManager.toggleFlashlight();
                vrInput.hapticRight(); // Haptic feedback
            }

            // Toggle elevator doors with X button (left controller)
            if (vrInput.isInteractButtonJustPressed()) {
                if (elevator != null) {
                    // Check if player is close enough to the elevator
                    final float distanceToElevator = elevator.getDistanceToPlayer(player.getPosition());
                    if (distanceToElevator <= 8.0f) { // Within 8 units
                        elevator.toggleDoors();
                        vrInput.hapticLeft(); // Haptic feedback
                    } else {
                        System.out.println("[GameScreen] Too far from elevator to control doors (distance: " + distanceToElevator + ")");
                    }
                }
            }

            // Exit game - handled by Oculus button (system)

        } else {
            // Desktop Mode - Keyboard input

            // Toggle flashlight
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
                lightingManager.toggleFlashlight();
            }

            // Toggle elevator doors with E key
            if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
                if (elevator != null) {
                    // Check if player is close enough to the elevator
                    final float distanceToElevator = elevator.getDistanceToPlayer(player.getPosition());
                    if (distanceToElevator <= 8.0f) { // Within 8 units
                        elevator.toggleDoors();
                    } else {
                        System.out.println("[GameScreen] Too far from elevator to control doors (distance: " + distanceToElevator + ")");
                    }
                }
            }

            // Exit game
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                Gdx.input.setCursorCatched(false);
                Gdx.app.exit();
            }
        }
    }

    /**
     * Gets the forward direction vector based on yaw.
     */
    private Vector3 getForwardVector() {
        final double radians = Math.toRadians(yaw);
        return new Vector3(
            (float) Math.sin(radians),
            0,
            -(float) Math.cos(radians)
        );
    }

    /**
     * Gets the right direction vector based on yaw.
     */
    private Vector3 getRightVector() {
        final double radians = Math.toRadians(yaw + 90);
        return new Vector3(
            (float) Math.sin(radians),
            0,
            -(float) Math.cos(radians)
        );
    }

    /**
     * Updates camera position and rotation based on player state.
     */
    private void updateCamera() {
        if (vrMode) {
            // VR mode - update VR camera controller
            vrCameraController.setBodyPosition(player.getPosition());
            vrCameraController.setBodyYaw(bodyYaw);
            vrCameraController.update();

            // Apply jumpscare shake would be handled differently in VR
            // TODO: Implement VR jumpscare effect (controller haptics + screen shake)

        } else {
            // Desktop mode - update standard camera
            camera.position.set(player.getPosition());

            // Apply jumpscare screen shake if active
            if (jumpscareActive) {
                camera.position.add(jumpscareShakeOffset);
            }

            // Calculate look direction
            final double yawRad = Math.toRadians(yaw);
            final double pitchRad = Math.toRadians(pitch);

            final float lookX = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
            final float lookY = (float) Math.sin(pitchRad);
            final float lookZ = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));

            camera.direction.set(lookX, lookY, lookZ).nor();
            camera.update();
        }
    }

    /**
     * Checks if the player has been caught by the enemy.
     */
    private void checkDeathCondition() {
        // Calculate horizontal distance only (ignore Y-axis height difference)
        final Vector3 playerPos = player.getPosition();
        final Vector3 enemyPos = enemy.getPosition();

        final float dx = playerPos.x - enemyPos.x;
        final float dz = playerPos.z - enemyPos.z;
        final float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance <= enemy.getCatchRadius()) {
            triggerDeath();
        }
    }

    /**
     * Triggers the death sequence with jumpscare effects.
     */
    private void triggerDeath() {
        if (isDead) return; // Already dead

        isDead = true;
        jumpscareActive = true;
        jumpscareTimer = 0f;

        System.out.println("[GameScreen] Player caught! Triggering jumpscare...");

        // Snap camera to face enemy
        final Vector3 toEnemy = enemy.getPosition().cpy().sub(player.getPosition());
        final float angleToEnemy = (float) Math.toDegrees(Math.atan2(toEnemy.x, -toEnemy.z));
        yaw = angleToEnemy;
        pitch = 0f; // Level camera
    }

    /**
     * Handles the jumpscare animation sequence.
     * Sequence: Red flash -> Screen shake -> Fade to black -> Death screen
     */
    private void handleJumpscare(final float delta) {
        jumpscareTimer += delta;

        final float progress = jumpscareTimer / GameConfig.JUMPSCARE_DURATION;

        // Phase 1 (0-0.3s): Red flash (quick fade out)
        if (jumpscareTimer < 0.3f) {
            final float flashAlpha = 1.0f - (jumpscareTimer / 0.3f);
            Gdx.gl.glClearColor(flashAlpha, 0f, 0f, 1f);
        }

        // Phase 2 (0-0.5s): Violent screen shake
        if (jumpscareTimer < 0.5f) {
            final float shakeIntensity = GameConfig.JUMPSCARE_SHAKE_INTENSITY * (1.0f - jumpscareTimer / 0.5f);
            jumpscareShakeOffset.set(
                (float) (Math.random() - 0.5) * shakeIntensity * 2,
                (float) (Math.random() - 0.5) * shakeIntensity * 2,
                (float) (Math.random() - 0.5) * shakeIntensity * 2
            );
        } else {
            jumpscareShakeOffset.set(0, 0, 0);
        }

        // Update camera to apply shake
        updateCamera();

        // Clear and render scene
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        mazeRenderer.render(camera);
        mazeRenderer.renderEnemy(camera, enemy);

        // Phase 3 (0.5s-1.5s): Fade to black overlay
        if (jumpscareTimer > 0.5f) {
            // Draw fading black overlay (simplified - would need SpriteBatch in real implementation)
            final float fadeAlpha = Math.min(1.0f, (jumpscareTimer - 0.5f) / 1.0f);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glClearColor(0f, 0f, 0f, fadeAlpha);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // Phase 4 (1.5s+): Transition to death screen
        if (jumpscareTimer >= GameConfig.JUMPSCARE_DURATION) {
            transitionToDeathScreen();
        }
    }

    /**
     * Transitions from the game screen to the death screen.
     */
    private void transitionToDeathScreen() {
        System.out.println("[GameScreen] Transitioning to death screen...");

        final DeathScreen deathScreen = new DeathScreen(survivalTime);

        // Initialize the death screen
        deathScreen.show();

        // Register and switch to death screen (GameApp handles disposing old screen)
        GameApp.addScreen("DeathScreen", deathScreen);
        GameApp.switchScreen("DeathScreen");
    }

    @Override
    public void resize(final int width, final int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void hide() {
        if (!vrMode) {
            Gdx.input.setCursorCatched(false);
        }

        // Clean up VR resources
        if (vrMode && vrManager != null) {
            if (stereoFramebufferManager != null) {
                stereoFramebufferManager.dispose();
            }
            vrManager.shutdown();
        }

        gameUI.dispose();
        mazeRenderer.dispose();
        materialManager.dispose();
        lightingManager.dispose();
    }

    /**
     * Helper method for wall sliding collision handling.
     * Tries to slide along walls when direct movement is blocked.
     *
     * @param moveDirection Desired movement direction
     * @param delta Time delta
     */
    private void handleWallSliding(final Vector3 moveDirection, final float delta) {
        // Try sliding along walls (X direction only)
        final Vector3 slideX = player.getPosition().cpy().add(moveDirection.x, 0, 0);
        if (!checkCollision(slideX)) {
            player.getPosition().set(slideX);
        } else {
            // Try sliding along walls (Z direction only)
            final Vector3 slideZ = player.getPosition().cpy().add(0, 0, moveDirection.z);
            if (!checkCollision(slideZ)) {
                player.getPosition().set(slideZ);
            }
            // If both fail, player is stuck in corner and doesn't move
        }
    }

    @Override
    public void dispose() {
        hide();
    }
}

