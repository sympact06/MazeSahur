package nl.saxion.game.mazesahur.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import nl.saxion.game.mazesahur.config.GameConfig;
import nl.saxion.game.mazesahur.entity.Player;
import nl.saxion.game.mazesahur.entity.Enemy;
import nl.saxion.game.mazesahur.entity.Elevator;
import nl.saxion.game.mazesahur.entity.PhotoFrame;
import nl.saxion.game.mazesahur.entity.Boost;
import nl.saxion.game.mazesahur.rendering.LightingManager;
import nl.saxion.game.mazesahur.rendering.MaterialManager;
import nl.saxion.game.mazesahur.rendering.MazeRenderer;
import nl.saxion.game.mazesahur.world.Maze;
import nl.saxion.game.mazesahur.ui.GameUI;
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
    private final List<PhotoFrame> photoFrames;
    private final List<Boost> boosts;

    // Rendering systems
    private LightingManager lightingManager;
    private MaterialManager materialManager;
    private MazeRenderer mazeRenderer;

    // UI
    private GameUI gameUI;

    // Audio
    private Sound flashlightToggleSound;
    private Sound sahurFootstepsSound;
    private long sahurFootstepsSoundId = -1;

    // Camera control
    private float yaw;
    private float pitch;
    private int lastMouseX;
    private int lastMouseY;
    private boolean firstMouse;

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

    // Debug visualization
    private boolean showRailNetwork = false;

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

        // Initialize photo frames on walls
        photoFrames = createPhotoFramesOnWalls();

        // Initialize boost pickups
        boosts = createBoostPickups();

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
        camera = new PerspectiveCamera(67, screenWidth, screenHeight);
        camera.near = 0.01f;
        camera.far = 100f;

        // Initialize rendering systems (requires OpenGL context)
        lightingManager = new LightingManager();
        materialManager = new MaterialManager();
        mazeRenderer = new MazeRenderer(maze, materialManager, lightingManager);

        // Load photo frames after renderer is initialized
        mazeRenderer.loadPhotoFrames(photoFrames);

        // Load boost pickups after renderer is initialized
        mazeRenderer.loadBoosts(boosts);

        // Initialize UI
        gameUI = new GameUI();
        gameUI.initialize();

        // Load audio
        flashlightToggleSound = Gdx.audio.newSound(Gdx.files.internal("audio/light-switch-81967.mp3"));

        // Load Sahur's heavy footsteps sound
        try {
            sahurFootstepsSound = Gdx.audio.newSound(Gdx.files.internal("audio/heavy-walking.mp3"));
            System.out.println("[GameScreen] Loaded Sahur footsteps sound");
        } catch (Exception e) {
            System.err.println("[GameScreen] Failed to load Sahur footsteps sound: " + e.getMessage());
        }

        // Capture cursor for FPS controls
        Gdx.input.setCursorCatched(true);

        // Update camera
        camera.position.set(player.getPosition());
        camera.lookAt(player.getPosition().x, player.getPosition().y, player.getPosition().z - 1);
        camera.update();

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

            // Update boosts and check for pickups
            for (Boost boost : boosts) {
                boost.update(delta);
                if (boost.tryCollect(player.getPosition())) {
                    // Activate boost on player
                    player.activateBoost(Boost.getBoostDuration(), Boost.getSpeedMultiplier());
                    System.out.println("[GameScreen] Boost collected! Speed increased for " + Boost.getBoostDuration() + " seconds");
                }
            }

            // Update lighting
            final boolean isMoving = player.isMoving();
            lightingManager.updateFlashlight(player.getPosition(), camera.direction, delta, isMoving);
            mazeRenderer.updateLampFlicker(delta);

            // Update footsteps
            mazeRenderer.updateFootsteps(delta, enemy);

            // Update Sahur footsteps audio based on distance
            updateSahurFootstepsAudio();

            // Handle input
            handleGameInput();
        }

        // Clear screen
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Render 3D scene (elevator is rendered together with maze to prevent material bleeding)
        mazeRenderer.renderWithElevator(camera, elevator);
        mazeRenderer.renderEnemy(camera, enemy);

        // Render boost pickups
        mazeRenderer.renderBoosts(camera, boosts);
        // Render footsteps
        mazeRenderer.renderFootsteps(camera);

        // Render debug visualizations
        if (showRailNetwork) {
            mazeRenderer.renderRailNetworkDebug(camera, enemy.getRailNetwork());
        }

        // Render UI (hide during jumpscare)
        if (!jumpscareActive) {
            gameUI.render(this, player, enemy, elevator, lightingManager);
        }
    }

    /**
     * Handles mouse look and WASD movement input.
     */
    private void handleInput(final float delta) {
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

        // Check if player is sprinting (Shift key)
        final boolean isSprinting = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        player.setSprinting(isSprinting);

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

        // Check if player is trying to run (Shift key)
        final boolean tryingToRun = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                                     || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        // Can only run if moving and has energy
        final boolean canRun = moveDirection.len() > 0 && tryingToRun && player.getEnergy() > 0.0f;
        player.setRunning(canRun);

        // Drain energy while running
        if (canRun) {
            player.drainEnergy(delta);
        }

        // Apply movement with collision detection
        if (moveDirection.len() > 0) {
            // Calculate speed based on energy level
            final float speedMultiplier = player.getCurrentSpeedMultiplier();
            final float currentSpeed = GameConfig.PLAYER_MOVE_SPEED * speedMultiplier;

            moveDirection.nor().scl(currentSpeed * delta);

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

    /**
     * Creates an elevator using the maze's guaranteed position finder.
     * The maze will find a suitable wall and prepare it for the elevator.
     */
    private Elevator createElevatorInOpenSpace() {
        // Player spawns at (12, 3, 12) world coordinates
        final float playerX = 12f;
        final float playerZ = 12f;

        final int playerGridX = (int) Math.floor(playerX / Maze.CELL_SIZE);
        final int playerGridZ = (int) Math.floor(playerZ / Maze.CELL_SIZE);

        // Let the maze find and prepare a guaranteed elevator position
        final int[] elevatorInfo = maze.findAndPrepareElevatorPosition(playerGridX, playerGridZ);

        // Extract position info
        final int wallX = elevatorInfo[0];
        final int wallZ = elevatorInfo[1];
        final int openX = elevatorInfo[2];
        final int openZ = elevatorInfo[3];
        final int direction = elevatorInfo[4];

        // Convert wall position to world coordinates
        final float[] wallWorldPos = maze.gridToWorld(wallX, wallZ);
        final float elevatorX = wallWorldPos[0];
        final float elevatorZ = wallWorldPos[1];

        // Direction names for debug
        final String[] dirNames = {"North (wall is North)", "East (wall is East)",
                                   "South (wall is South)", "West (wall is West)"};

        System.out.println("[GameScreen] ===== ELEVATOR SPAWN =====");
        System.out.println("[GameScreen] Player spawn: (" + playerX + ", " + playerZ + ")");
        System.out.println("[GameScreen] Elevator grid (IN WALL): (" + wallX + ", " + wallZ + ")");
        System.out.println("[GameScreen] Elevator world: (" + elevatorX + ", " + elevatorZ + ")");
        System.out.println("[GameScreen] Open space grid: (" + openX + ", " + openZ + ")");
        System.out.println("[GameScreen] Door faces: " + dirNames[direction]);
        System.out.println("[GameScreen] ===========================");

        return new Elevator(maze, elevatorX, elevatorZ);
    }

    /**
     * Creates photo frames on walls throughout the maze.
     * Frames contain commemorative photo of the elevator.
     * Spawns randomly with ~10% chance on suitable wall segments.
     */
    private List<PhotoFrame> createPhotoFramesOnWalls() {
        final List<PhotoFrame> frames = new ArrayList<>();
        final Random random = new Random();
        final float spawnChance = 0.1f; // 10% chance per suitable wall

        System.out.println("[GameScreen] ===== PHOTO FRAME SPAWN DEBUG =====");

        // Iterate through all maze cells
        for (int gridZ = 1; gridZ < maze.getHeight() - 1; gridZ++) {
            for (int gridX = 1; gridX < maze.getWidth() - 1; gridX++) {
                // Only look at corridor cells (not walls)
                if (!maze.isWall(gridX, gridZ)) {
                    // Check each wall direction
                    checkAndSpawnFrame(frames, random, gridX, gridZ, 0, -1, PhotoFrame.WallFace.NORTH, spawnChance);
                    checkAndSpawnFrame(frames, random, gridX, gridZ, 0, 1, PhotoFrame.WallFace.SOUTH, spawnChance);
                    checkAndSpawnFrame(frames, random, gridX, gridZ, 1, 0, PhotoFrame.WallFace.EAST, spawnChance);
                    checkAndSpawnFrame(frames, random, gridX, gridZ, -1, 0, PhotoFrame.WallFace.WEST, spawnChance);
                }
            }
        }

        System.out.println("[GameScreen] Created " + frames.size() + " photo frames throughout maze");
        System.out.println("[GameScreen] =====================================");

        return frames;
    }

    /**
     * Helper method to check if a frame should spawn on a wall and add it if so.
     */
    private void checkAndSpawnFrame(final List<PhotoFrame> frames, final Random random,
                                     final int gridX, final int gridZ,
                                     final int dx, final int dz,
                                     final PhotoFrame.WallFace wallFace,
                                     final float spawnChance) {
        final int wallX = gridX + dx;
        final int wallZ = gridZ + dz;

        // Check if there's a wall in this direction
        if (wallX >= 0 && wallX < maze.getWidth() &&
            wallZ >= 0 && wallZ < maze.getHeight() &&
            maze.isWall(wallX, wallZ)) {

            // Random chance to spawn frame
            if (random.nextFloat() < spawnChance) {
                // Calculate world position (center of corridor cell, offset very close to wall)
                final float worldX = gridX * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f + (dx * Maze.CELL_SIZE * 0.49f);
                final float worldZ = gridZ * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f + (dz * Maze.CELL_SIZE * 0.49f);

                frames.add(new PhotoFrame(maze, worldX, worldZ, wallFace));
            }
        }
    }

    /**
     * Creates boost pickups randomly in the maze.
     * Spawns in open areas away from walls and spawn points.
     */
    private List<Boost> createBoostPickups() {
        final List<Boost> boostList = new ArrayList<>();
        final Random random = new Random();
        final int targetBoostCount = 8; // Spawn 8 boosts throughout the maze
        int attempts = 0;
        final int maxAttempts = 1000;

        System.out.println("[GameScreen] ===== BOOST PICKUP SPAWN DEBUG =====");

        while (boostList.size() < targetBoostCount && attempts < maxAttempts) {
            attempts++;

            // Random grid position
            final int gridX = random.nextInt(maze.getWidth());
            final int gridZ = random.nextInt(maze.getHeight());

            // Check if it's an open area
            if (!maze.isWall(gridX, gridZ)) {
                final float worldX = gridX * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
                final float worldZ = gridZ * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;

                // Check distance from player spawn
                final float dx = worldX - 12f;
                final float dz = worldZ - 12f;
                final float distFromSpawn = (float) Math.sqrt(dx * dx + dz * dz);

                // Don't spawn too close to player spawn
                if (distFromSpawn > 16f) {
                    // Check it's not too close to other boosts
                    boolean tooClose = false;
                    for (Boost existingBoost : boostList) {
                        final float bx = existingBoost.getPosition().x - worldX;
                        final float bz = existingBoost.getPosition().z - worldZ;
                        final float dist = (float) Math.sqrt(bx * bx + bz * bz);
                        if (dist < 20f) { // Minimum 20 units apart
                            tooClose = true;
                            break;
                        }
                    }

                    if (!tooClose) {
                        boostList.add(new Boost(worldX, worldZ));
                        System.out.println("[GameScreen] Boost " + boostList.size() + " spawned at (" + worldX + ", " + worldZ + ")");
                    }
                }
            }
        }

        System.out.println("[GameScreen] Created " + boostList.size() + " boost pickups");
        System.out.println("[GameScreen] =========================================");

        return boostList;
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

        // TODO: Re-enable door frame collision after testing spawn position
        // Check collision with elevator door frame (walls beside the door)
        // if (elevator.collidesWithDoorFrame(position, GameConfig.PLAYER_COLLISION_RADIUS)) {
        //     return true;
        // }

        return false;
    }

    /**
     * Handles non-movement game input (flashlight toggle, exit, elevator control, etc.).
     */
    private void handleGameInput() {
        // Toggle flashlight
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            lightingManager.toggleFlashlight();
            // Play light switch sound
            if (flashlightToggleSound != null) {
                flashlightToggleSound.play(0.7f); // Volume at 70%
            }
        }

        // Toggle rail network visualization (debug)
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            showRailNetwork = !showRailNetwork;
            System.out.println("[GameScreen] Rail network visualization: "
                + (showRailNetwork ? "ON" : "OFF"));
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
        camera.position.set(player.getPosition());

        // Apply jumpscare screen shake if active
        if (jumpscareActive) {
            camera.position.add(jumpscareShakeOffset);
        }

        // Apply FOV effect when sprinting (speed effect)
        final float targetFOV = player.isSprinting() ? GameConfig.FIELD_OF_VIEW + 10f : GameConfig.FIELD_OF_VIEW;
        // Smoothly interpolate FOV for smooth transition
        camera.fieldOfView += (targetFOV - camera.fieldOfView) * 0.1f;

        // Calculate look direction
        final double yawRad = Math.toRadians(yaw);
        final double pitchRad = Math.toRadians(pitch);

        final float lookX = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        final float lookY = (float) Math.sin(pitchRad);
        final float lookZ = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));

        camera.direction.set(lookX, lookY, lookZ).nor();
        camera.update();
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

        // Stop Sahur footsteps sound
        if (sahurFootstepsSound != null && sahurFootstepsSoundId != -1) {
            sahurFootstepsSound.stop(sahurFootstepsSoundId);
            sahurFootstepsSoundId = -1;
        }

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

    /**
     * Updates Sahur's heavy footsteps audio based on distance from the player.
     * The sound gets louder as Sahur gets closer and quieter as he moves away.
     */
    private void updateSahurFootstepsAudio() {
        if (sahurFootstepsSound == null) {
            return; // Sound not loaded
        }

        // Calculate distance between player and Sahur
        final float distance = player.getPosition().dst(enemy.getPosition());

        // Define distance thresholds for audio
        final float maxHearingDistance = 50f; // Can hear from up to 50 units away
        final float minDistance = 5f; // Minimum distance for volume calculation

        // If Sahur is too far away, stop the sound
        if (distance > maxHearingDistance) {
            if (sahurFootstepsSoundId != -1) {
                sahurFootstepsSound.stop(sahurFootstepsSoundId);
                sahurFootstepsSoundId = -1;
            }
            return;
        }

        // Calculate volume based on distance (inverse distance falloff)
        // Closer = louder, farther = quieter
        final float normalizedDistance = Math.max(0f, Math.min(1f, (distance - minDistance) / (maxHearingDistance - minDistance)));
        final float volume = (1f - normalizedDistance) * 0.8f; // Max volume 80%

        // Start or update the looping sound
        if (sahurFootstepsSoundId == -1) {
            // Start playing the sound in a loop
            sahurFootstepsSoundId = sahurFootstepsSound.loop(volume);
            System.out.println("[GameScreen] Started Sahur footsteps audio (distance: " + (int)distance + ", volume: " + String.format("%.2f", volume) + ")");
        } else {
            // Update the volume of the existing sound
            sahurFootstepsSound.setVolume(sahurFootstepsSoundId, volume);
        }
    }

    @Override
    public void resize(final int width, final int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);

        // Stop Sahur footsteps sound
        if (sahurFootstepsSound != null && sahurFootstepsSoundId != -1) {
            sahurFootstepsSound.stop(sahurFootstepsSoundId);
            sahurFootstepsSoundId = -1;
        }

        gameUI.dispose();
        mazeRenderer.dispose();
        materialManager.dispose();
        lightingManager.dispose();

        if (flashlightToggleSound != null) {
            flashlightToggleSound.dispose();
        }
        if (sahurFootstepsSound != null) {
            sahurFootstepsSound.dispose();
        }
    }

    @Override
    public void dispose() {
        hide();
    }
}

