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
import nl.saxion.game.mazesahur.event.EventManager;
import nl.saxion.game.mazesahur.event.HorrorEvent;
import nl.saxion.game.mazesahur.model.CharacterType;
import nl.saxion.game.mazesahur.net.MultiplayerSession;
import nl.saxion.game.mazesahur.net.RemotePlayerState;
import nl.saxion.game.mazesahur.config.GameConfig;
import nl.saxion.game.mazesahur.entity.Player;
import nl.saxion.game.mazesahur.entity.Enemy;
import nl.saxion.game.mazesahur.entity.PhotoFrame;
import nl.saxion.game.mazesahur.entity.PolicePinboard;
import nl.saxion.game.mazesahur.entity.Boost;
import nl.saxion.game.mazesahur.rendering.LightingManager;
import nl.saxion.game.mazesahur.rendering.MaterialManager;
import nl.saxion.game.mazesahur.rendering.MazeRenderer;
import nl.saxion.game.mazesahur.rendering.ResourceManager;
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
    private Enemy enemy;
    private Maze maze;
    private List<PhotoFrame> photoFrames;
    private List<PolicePinboard> policePinboards;
    private List<Boost> boosts;

    // Rendering systems
    private LightingManager lightingManager;
    private MaterialManager materialManager;
    private MazeRenderer mazeRenderer;

    // UI
    private GameUI gameUI;

    // Audio
    private Sound flashlightToggleSound;

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

    // Multiplayer
    private final MultiplayerSession multiplayerSession;
    private final boolean networked;
    private List<RemotePlayerState> remotePlayers = new ArrayList<>();
    private boolean useNetworkEnemy = false;
    private final CharacterType localCharacterType;
    private EventManager eventManager;
    private float stressLevel = 0f;
    private float contextSendTimer = 0f;
    private static final float CONTEXT_INTERVAL = 0.75f;
    private final Vector3 eventCameraOffset = new Vector3();

    // Horror audio (optional placeholders)
    private Sound whisperSound;
    private Sound rushSound;
    private Sound hallucinationSound;

    // Level system
    private int currentLevel = 1;
    private Vector3 exitPosition = new Vector3();
    private boolean showExitMarker = true;
    private boolean showExitESP = false;

    /**
     * Creates a new game screen with default settings.
     */
    public GameScreen() {
        this(null, null, CharacterSelectionScreen.getSavedCharacter());
    }

    /**
     * Creates a new game screen with an optional deterministic maze seed.
     * Passing a seed allows server/client to share the exact same layout.
     *
     * @param mazeSeed Seed to use for maze generation (null = random)
     */
    public GameScreen(final Long mazeSeed) {
        this(mazeSeed, null, CharacterSelectionScreen.getSavedCharacter());
    }

    /**
     * Creates a new game screen with optional seed and multiplayer session.
     *
     * @param mazeSeed Seed to use for maze generation (null = random)
     * @param session Multiplayer session (null for singleplayer)
     */
    public GameScreen(final Long mazeSeed, final MultiplayerSession session) {
        this(mazeSeed, session, CharacterSelectionScreen.getSavedCharacter());
    }

    /**
     * Creates a new game screen with optional seed, multiplayer session, and character selection.
     *
     * @param mazeSeed Seed to use for maze generation (null = random)
     * @param session Multiplayer session (null for singleplayer)
     * @param characterType Character skin to use (persisted to networking)
     */
    public GameScreen(final Long mazeSeed, final MultiplayerSession session, final CharacterType characterType) {
        super(1280, 720);

        this.multiplayerSession = session;
        this.networked = session != null;
        this.localCharacterType = characterType != null ? characterType : CharacterType.DEFAULT;

        final long seed = mazeSeed != null
            ? mazeSeed
            : (session != null ? session.getSeed() : System.currentTimeMillis());

        // Initialize world
        maze = new Maze(25, 25, seed);
        maze.generate();

        // Initialize entities
        player = new Player(new Vector3(12f, 3f, 12f));
        enemy = new Enemy(maze, player);

        // Initialize photo frames on walls
        photoFrames = createPhotoFramesOnWalls();

        // Initialize police pinboards on walls
        policePinboards = createPolicePinboards();

        // Initialize boost pickups
        boosts = createBoostPickups();

        // Camera control initialization
        yaw = 0;
        pitch = 0;
        firstMouse = true;

        // Register level change listener for multiplayer
        if (networked && session != null) {
            session.setLevelChangeListener(new MultiplayerSession.LevelChangeListener() {
                @Override
                public void onLevelChanged(final int newLevel, final long newSeed,
                                           final float exitX, final float exitZ,
                                           final float spawnX, final float spawnZ) {
                    // CRITICAL: Must run on rendering thread for OpenGL operations
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            handleLevelChange(newLevel, newSeed, exitX, exitZ, spawnX, spawnZ);
                        }
                    });
                }
            });

            // Haal huidige level info op (voor late join)
            currentLevel = session.getCurrentLevel();
            exitPosition.set(session.getExitX(), GameConfig.PLAYER_HEIGHT, session.getExitZ());
        } else {
            // Singleplayer: exit positie direct bepalen
            findExitLocation();
        }

        System.out.println("[GameScreen] Using maze seed: " + seed + " networked=" + networked
            + " character=" + localCharacterType.name());
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
        System.out.println("[GameScreen] Screen dimensions at show(): " + screenWidth + "x" + screenHeight);
        camera = new PerspectiveCamera(67, screenWidth, screenHeight);
        camera.near = 0.01f;
        camera.far = 100f;

        // Initialize rendering systems (requires OpenGL context)
        lightingManager = new LightingManager();

        // Use pre-loaded materials if available, otherwise create new MaterialManager
        materialManager = ResourceManager.getInstance().getMaterialManager();
        if (materialManager == null) {
            System.out.println("[GameScreen] Materials not pre-loaded, creating new MaterialManager");
            materialManager = new MaterialManager();
        } else {
            System.out.println("[GameScreen] Using pre-loaded materials from ResourceManager");
        }

        mazeRenderer = new MazeRenderer(maze, materialManager, lightingManager);

        // Load photo frames after renderer is initialized
        mazeRenderer.loadPhotoFrames(photoFrames);

        // Load police pinboards after renderer is initialized
        mazeRenderer.loadPolicePinboards(policePinboards);

        // Load boost pickups after renderer is initialized
        mazeRenderer.loadBoosts(boosts);

        // Initialize UI
        gameUI = new GameUI();
        gameUI.initialize();

        // Load audio - use pre-loaded sound if available
        flashlightToggleSound = ResourceManager.getInstance().getSound("flashlight_toggle");
        if (flashlightToggleSound == null) {
            System.out.println("[GameScreen] Flashlight sound not pre-loaded, loading on-demand...");
            flashlightToggleSound = Gdx.audio.newSound(Gdx.files.internal("audio/light-switch-81967.mp3"));
        } else {
            System.out.println("[GameScreen] Using pre-loaded flashlight sound");
        }

        // Load Sahur's heavy footsteps sound
        try {
//            sahurFootstepsSound = Gdx.audio.newSound(Gdx.files.internal("audio/heavy-walking.mp3"));
            System.out.println("[GameScreen] Loaded Sahur footsteps sound");
        } catch (Exception e) {
            System.err.println("[GameScreen] Failed to load Sahur footsteps sound: " + e.getMessage());
        }
        // Load audio
        flashlightToggleSound = Gdx.audio.newSound(Gdx.files.internal("audio/light-switch-81967.mp3"));
        whisperSound = loadOptionalSound("audio/placeholder_whisper.wav");
        rushSound = loadOptionalSound("audio/placeholder_rush.wav");
        hallucinationSound = loadOptionalSound("audio/placeholder_shadow.wav");

        // Capture cursor for FPS controls
        Gdx.input.setCursorCatched(true);

        // Force viewport to full window size
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        System.out.println("[GameScreen] Set initial viewport to " + screenWidth + "x" + screenHeight);

        // Update camera
        camera.position.set(player.getPosition());
        camera.lookAt(player.getPosition().x, player.getPosition().y, player.getPosition().z - 1);
        camera.update();

        // Initialize rendering systems
        // Only load textures if they weren't pre-loaded
        if (!ResourceManager.getInstance().areMaterialsLoaded()) {
            System.out.println("[GameScreen] Materials not pre-loaded, loading now...");
            materialManager.loadTextures();
        } else {
            System.out.println("[GameScreen] Skipping material loading (already pre-loaded)");
        }
        mazeRenderer.initialize();

        // Initialize enemy position
        enemy.initialize();

        // Initialize horror event manager
        eventManager = new EventManager(
            maze,
            player,
            enemy,
            lightingManager,
            networked,
            whisperSound,
            rushSound,
            hallucinationSound
        );

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
            // Apply latest network state before local updates
            if (networked && multiplayerSession != null && multiplayerSession.isJoined()) {
                syncNetworkState();
            }

            // Update event system and stress before game logic
            stressLevel = computeStressLevel();
            if (eventManager != null) {
                if (networked) {
                    handleIncomingNetworkEvents();
                }
                eventManager.update(delta, stressLevel);
                eventCameraOffset.set(eventManager.getCameraOffset());
                maybeSendContextToServer(delta);
            }

            // Update game state
            handleInput(delta);
            player.update(delta, maze);
            if (!useNetworkEnemy) {
                enemy.update(delta);
            }
            updateCamera();

            // Singleplayer exit check
            if (!networked) {
                checkSingleplayerExit();
            }

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

            // Handle input
            handleGameInput();
        }

        // Clear screen
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Set viewport to full window size (fixes bottom-left corner rendering issue)
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

        // Render 3D scene
        mazeRenderer.render(camera);
        mazeRenderer.renderEnemy(camera, enemy);

        // Render boost pickups
        mazeRenderer.renderBoosts(camera, boosts);

        // Render exit marker
        if (showExitMarker) {
            mazeRenderer.renderExitMarker(camera, exitPosition);
        }

        // Render exit ESP (door muren zichtbaar) als ingeschakeld
        if (showExitESP) {
            mazeRenderer.renderExitESP(camera, exitPosition, player.getPosition());
        }

        // Render remote players (if any)
        if (networked) {
            mazeRenderer.renderRemotePlayers(camera, remotePlayers);
        }
        // Render footsteps
        mazeRenderer.renderFootsteps(camera);

        // Render debug visualizations
        if (showRailNetwork) {
            mazeRenderer.renderRailNetworkDebug(camera, enemy.getRailNetwork());
        }

        // Render UI (hide during jumpscare)
        if (!jumpscareActive) {
            gameUI.render(this, player, enemy, lightingManager, camera, remotePlayers,
                         showExitESP, exitPosition, yaw);
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
        // Always send input when networked so server knows when you stop moving
        if (networked && multiplayerSession != null && multiplayerSession.isJoined()) {
            final boolean hasInput = moveDirection.len2() > 0.0001f;
            multiplayerSession.sendInput(moveDirection.x, moveDirection.z, yaw);
            if (hasInput) {
                final float speedMultiplier = player.getCurrentSpeedMultiplier();
                final float currentSpeed = GameConfig.PLAYER_MOVE_SPEED * speedMultiplier;
                final Vector3 predicted = player.getPosition().cpy()
                    .add(moveDirection.nor().scl(currentSpeed * delta));
                if (!checkCollision(predicted)) {
                    player.getPosition().set(predicted);
                }
            }
            return;
        }

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
     * Computes a rough stress level (0..1) based on proximity, light, and energy.
     */
    private float computeStressLevel() {
        float stress = 0f;

        // Enemy proximity (dominant factor)
        final float distance = player.getPosition().dst(enemy.getPosition());
        stress += Math.max(0f, (15f - distance) / 15f) * 0.6f;

        // Flashlight status
        if (!lightingManager.isFlashlightEnabled()) {
            stress += 0.2f;
        }

        // Low energy increases stress
        if (player.getEnergy() < 0.4f) {
            stress += (0.4f - player.getEnergy());
        }

        return Math.min(1f, stress);
    }

    private void handleIncomingNetworkEvents() {
        if (multiplayerSession == null || eventManager == null) {
            return;
        }
        final List<HorrorEvent> events = multiplayerSession.drainEvents();
        for (HorrorEvent event : events) {
            eventManager.enqueue(event);
        }
    }

    /**
     * Sends lightweight context to the server so it can pace events.
     */
    private void maybeSendContextToServer(final float delta) {
        if (!networked || multiplayerSession == null) {
            return;
        }
        contextSendTimer += delta;
        if (contextSendTimer < CONTEXT_INTERVAL) {
            return;
        }
        contextSendTimer = 0f;

        final float distance = player.getPosition().dst(enemy.getPosition());
        final boolean flashlightOn = lightingManager.isFlashlightEnabled();
        multiplayerSession.sendContext(stressLevel, flashlightOn, distance);
    }

    /**
     * Creates photo frames on walls throughout the maze.
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
     * Creates police pinboards on walls at specific locations throughout the maze.
     * Spawns at a few strategic locations to add atmosphere.
     */
    private List<PolicePinboard> createPolicePinboards() {
        final List<PolicePinboard> pinboards = new ArrayList<>();
        final Random random = new Random(maze.getWidth() * maze.getHeight()); // Consistent seed
        final float spawnChance = 0.03f; // 3% chance per suitable wall (less common than photos)

        System.out.println("[GameScreen] ===== POLICE PINBOARD SPAWN DEBUG =====");

        // Iterate through all maze cells
        for (int gridZ = 1; gridZ < maze.getHeight() - 1; gridZ++) {
            for (int gridX = 1; gridX < maze.getWidth() - 1; gridX++) {
                // Only look at corridor cells (not walls)
                if (!maze.isWall(gridX, gridZ)) {
                    // Check each wall direction
                    checkAndSpawnPinboard(pinboards, random, gridX, gridZ, 0, -1, PolicePinboard.WallFace.NORTH, spawnChance);
                    checkAndSpawnPinboard(pinboards, random, gridX, gridZ, 0, 1, PolicePinboard.WallFace.SOUTH, spawnChance);
                    checkAndSpawnPinboard(pinboards, random, gridX, gridZ, 1, 0, PolicePinboard.WallFace.EAST, spawnChance);
                    checkAndSpawnPinboard(pinboards, random, gridX, gridZ, -1, 0, PolicePinboard.WallFace.WEST, spawnChance);
                }
            }
        }

        System.out.println("[GameScreen] Created " + pinboards.size() + " police pinboards throughout maze");
        System.out.println("[GameScreen] =====================================");

        return pinboards;
    }

    /**
     * Helper method to check if a pinboard should spawn on a wall and add it if so.
     */
    private void checkAndSpawnPinboard(final List<PolicePinboard> pinboards, final Random random,
                                        final int gridX, final int gridZ,
                                        final int dx, final int dz,
                                        final PolicePinboard.WallFace wallFace,
                                        final float spawnChance) {
        final int wallX = gridX + dx;
        final int wallZ = gridZ + dz;

        // Check if there's a wall in this direction
        if (wallX >= 0 && wallX < maze.getWidth() &&
            wallZ >= 0 && wallZ < maze.getHeight() &&
            maze.isWall(wallX, wallZ)) {

            // Random chance to spawn pinboard
            if (random.nextFloat() < spawnChance) {
                // Calculate world position (center of corridor cell, offset very close to wall)
                final float worldX = gridX * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f + (dx * Maze.CELL_SIZE * 0.49f);
                final float worldZ = gridZ * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f + (dz * Maze.CELL_SIZE * 0.49f);

                pinboards.add(new PolicePinboard(maze, worldX, worldZ, wallFace));
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
     * Checks collision with maze walls using circular collision detection.
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

        return false;
    }

    /**
     * Handles non-movement game input (flashlight toggle, exit, etc.).
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

        // Toggle exit ESP (door muren zichtbaar)
        if (Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            showExitESP = !showExitESP;
            System.out.println("[GameScreen] Exit ESP: " + (showExitESP ? "ON" : "OFF"));
        }

        // Exit game
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.input.setCursorCatched(false);
            Gdx.app.exit();
        }
    }

    /**
     * Loads a sound if the asset exists; otherwise returns null without failing.
     */
    private Sound loadOptionalSound(final String path) {
        try {
            if (Gdx.files.internal(path).exists()) {
                return Gdx.audio.newSound(Gdx.files.internal(path));
            }
        } catch (Exception e) {
            System.err.println("[GameScreen] Failed to load sound " + path + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Applies latest authoritative state from the multiplayer session.
     */
    private void syncNetworkState() {
        if (multiplayerSession == null) {
            return;
        }

        final RemotePlayerState self = multiplayerSession.getSelfState();
        if (self != null) {
            final Vector3 target = new Vector3(self.x, self.y, self.z);
            final float dist = player.getPosition().dst(target);
            if (dist > 1.0f) {
                // Large correction, snap
                player.getPosition().set(target);
            } else {
                // Smooth blend
                player.getPosition().lerp(target, 0.1f);
            }
            // Keep local yaw from mouse; server yaw can lag and cause camera snaps
        }
        remotePlayers = multiplayerSession.getRemotePlayers();

        final var enemySnap = multiplayerSession.getEnemySnapshot();
        if (enemySnap != null) {
            useNetworkEnemy = true;
            enemy.getPosition().set(enemySnap.x, enemySnap.y, enemySnap.z);
            enemy.setYaw(enemySnap.yaw);
        } else {
            useNetworkEnemy = false;
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
        if (eventManager != null) {
            camera.position.add(eventCameraOffset);
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
        if (camera != null) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            camera.update();
            System.out.println("[GameScreen] Resized to " + width + "x" + height);
        }
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);
        gameUI.dispose();
        mazeRenderer.dispose();
        materialManager.dispose();
        lightingManager.dispose();
        // Only dispose flashlight sound if we loaded it ourselves (not from ResourceManager)
        Sound preloadedSound = ResourceManager.getInstance().getSound("flashlight_toggle");
        if (flashlightToggleSound != null && flashlightToggleSound != preloadedSound) {
            flashlightToggleSound.dispose();
        }
        if (whisperSound != null) {
            whisperSound.dispose();
        }
        if (rushSound != null) {
            rushSound.dispose();
        }
        if (hallucinationSound != null) {
            hallucinationSound.dispose();
        }
    }

    @Override
    public void dispose() {
        hide();
    }

    /**
     * Required by InputProcessor interface (libGDX compatibility).
     * Called when the mouse wheel is scrolled.
     */
    public boolean scrolled(final int amount) {
        return false;
    }

    /**
     * Wordt aangeroepen wanneer de server een level change broadcast stuurt.
     * Regenereert de maze en reset de speler.
     */
    private void handleLevelChange(final int newLevel, final long newSeed,
                                    final float exitX, final float exitZ,
                                    final float spawnX, final float spawnZ) {
        System.out.println("[GameScreen] ====== LEVEL CHANGE TO LEVEL " + newLevel + " ======");

        currentLevel = newLevel;

        // Vernietig oude maze renderer
        if (mazeRenderer != null) {
            mazeRenderer.dispose();
        }

        // Genereer nieuwe maze
        maze = new Maze(GameConfig.MAZE_SIZE, GameConfig.MAZE_SIZE, newSeed);
        maze.generate();

        // Herbouw renderer
        mazeRenderer = new MazeRenderer(maze, materialManager, lightingManager);
        mazeRenderer.initialize();

        // Herlaad photo frames en boosts voor nieuwe level
        photoFrames = createPhotoFramesOnWalls();
        mazeRenderer.loadPhotoFrames(photoFrames);

        policePinboards = createPolicePinboards();
        mazeRenderer.loadPolicePinboards(policePinboards);

        boosts = createBoostPickups();
        mazeRenderer.loadBoosts(boosts);

        // Update exit positie
        exitPosition.set(exitX, GameConfig.PLAYER_HEIGHT, exitZ);

        // Reset speler positie
        player.getPosition().set(spawnX, GameConfig.PLAYER_HEIGHT, spawnZ);

        // Recreate enemy with new maze (Enemy has final Maze reference)
        enemy = new Enemy(maze, player);
        enemy.initialize();

        // Recreate event manager with new maze and enemy
        eventManager = new EventManager(
            maze,
            player,
            enemy,
            lightingManager,
            networked,
            whisperSound,
            rushSound,
            hallucinationSound
        );

        System.out.println("[GameScreen] Level change complete!");
    }

    /**
     * Vindt exit locatie voor singleplayer (zelfde algoritme als server).
     */
    private void findExitLocation() {
        // Spawn is at world position (12, 12), NOT grid position
        final float spawnX = 12f;
        final float spawnZ = 12f;

        float bestDist = 0f;
        int bestGridX = -1;
        int bestGridZ = -1;

        for (int z = 1; z < maze.getHeight() - 1; z++) {
            for (int x = 1; x < maze.getWidth() - 1; x++) {
                if (!maze.isWall(x, z)) {
                    final float worldX = x * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;
                    final float worldZ = z * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f;

                    final float dx = worldX - spawnX;
                    final float dz = worldZ - spawnZ;
                    final float dist = (float) Math.sqrt(dx * dx + dz * dz);

                    if (dist > bestDist) {
                        bestDist = dist;
                        bestGridX = x;
                        bestGridZ = z;
                    }
                }
            }
        }

        if (bestGridX == -1) {
            bestGridX = maze.getWidth() - 2;
            bestGridZ = maze.getHeight() - 2;
        }

        exitPosition.set(
            bestGridX * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f,
            GameConfig.PLAYER_HEIGHT,
            bestGridZ * Maze.CELL_SIZE + Maze.CELL_SIZE / 2f
        );
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    private void checkSingleplayerExit() {
        final float dx = player.getPosition().x - exitPosition.x;
        final float dz = player.getPosition().z - exitPosition.z;
        final float distSquared = dx * dx + dz * dz;

        if (distSquared < 2.5f * 2.5f) {
            handleSingleplayerLevelTransition();
        }
    }

    private void handleSingleplayerLevelTransition() {
        currentLevel++;
        if (currentLevel > 5) {
            currentLevel = 1;  // Loop terug naar level 1
        }

        System.out.println("[GameScreen] Singleplayer level transition to " + currentLevel);

        // Nieuwe seed
        final long newSeed = System.currentTimeMillis() ^ currentLevel;

        // Simuleer server level change
        handleLevelChange(currentLevel, newSeed, 0, 0, 12f, 12f);

        // Exit opnieuw vinden (omdat handleLevelChange niet automatisch doet voor SP)
        findExitLocation();
    }
}
