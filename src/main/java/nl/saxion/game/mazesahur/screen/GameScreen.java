package nl.saxion.game.mazesahur.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
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
    private List<Boost> boosts;

    // Rendering systems
    private LightingManager lightingManager;
    private MaterialManager materialManager;
    private MazeRenderer mazeRenderer;

    // UI
    private GameUI gameUI;

    // Audio
    private Sound flashlightToggleSound;
    private Sound enemyProximitySound;
    private long enemyProximitySoundId = -1L;

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
    private final Vector3 forwardVector = new Vector3();
    private final Vector3 rightVector = new Vector3();
    private final Vector3 movementInput = new Vector3();
    private final Vector3 collisionTestPosition = new Vector3();
    private final Vector3 networkSyncTarget = new Vector3();

    // Horror audio (optional placeholders)
    private Sound whisperSound;
    private Sound rushSound;
    private Sound hallucinationSound;
    private Sound demonicLaughterSound;
    private long demonicLaughterSoundId = -1L;
    private boolean demonicLaughterActive = false;
    private float demonicLaughterTimer = 0f;
    private float demonicLaughterIntervalTimer = 0f;
    private float demonicLaughterFlashToggleTimer = 0f;
    private boolean randomFlickerActive = false;
    private float randomFlickerTimer = 0f;
    private float randomFlickerCheckTimer = 0f;
    private boolean redFlickerActive = false;
    private float redFlickerTimer = 0f;
    private float redFlickerWindowTimer = 0f;
    private float redFlickerTriggerAt = 0f;
    private float redFlickerStartDelayTimer = 0f;
    private boolean redFlickerScheduled = false;
    private float globalEventTimer = 0f;
    private float globalEventStartDelayTimer = 0f;

    // Level system
    private int currentLevel = 1;
    private Vector3 exitPosition = new Vector3();
    private boolean showExitMarker = true;
    private boolean showExitESP = false;
    private boolean returnToMenuQueued = false;

    // Level loading screen (used for world transitions)
    private static final int LOADING_BAR_WIDTH = 700;
    private static final int LOADING_BAR_HEIGHT = 8;
    private static final int LOADING_BAR_BOTTOM_MARGIN = 40;
    private static final float LEVEL_LOADING_MIN_TIME = 0.6f;
    private static final float LEVEL_LOADING_POST_TIME = 0.2f;
    private boolean levelLoadingActive = false;
    private boolean levelChangeApplied = false;
    private float levelLoadingTimer = 0f;
    private PendingLevelChange pendingLevelChange;
    private SpriteBatch loadingBatch;
    private ShapeRenderer loadingShapeRenderer;
    private BitmapFont loadingFont;
    private Texture loadingSplashImage;

    // Proximity jumpscare
    private static final float PROXIMITY_JUMPSCARE_DISTANCE = 50.0f;
    private static final float PROXIMITY_JUMPSCARE_DURATION = 5.0f;
    private static final float PROXIMITY_JUMPSCARE_COOLDOWN = 45.0f;
    private static final float RANDOM_JUMPSCARE_INTERVAL = 160.0f;
    private static final float RANDOM_JUMPSCARE_CHANCE = 1.0f;
    private static final float DEMONIC_LAUGHTER_CHECK_INTERVAL = 1.0f;
    private static final float DEMONIC_LAUGHTER_CHANCE_PER_CHECK = 2.0f / 120.0f;
    private static final float DEMONIC_LAUGHTER_DURATION = 7.0f;
    private static final float DEMONIC_LAUGHTER_FLICKER_DURATION = 1.0f;
    private static final float DEMONIC_LAUGHTER_FLICKER_INTERVAL = 0.12f;
    private static final float DEMONIC_LAUGHTER_BLACKOUT_DURATION = 5.0f;
    private static final float RANDOM_FLICKER_CHECK_INTERVAL = 1.0f;
    private static final float RANDOM_FLICKER_CHANCE_PER_CHECK = 1.0f / 60.0f;
    private static final float RANDOM_FLICKER_DURATION = 5.0f;
    private static final float RANDOM_FLICKER_INTERVAL = 0.12f;
    private static final float RED_FLICKER_DURATION = 10.0f;
    private static final float RED_FLICKER_WINDOW = 120.0f;
    private static final float RED_FLICKER_START_DELAY = 10.0f;
    private static final float RED_FLICKER_INTERVAL = 0.12f;
    private static final Vector3 RED_FLICKER_COLOR_BRIGHT = new Vector3(2.0f, 0.2f, 0.2f);
    private static final Vector3 RED_FLICKER_COLOR_DIM = new Vector3(0.9f, 0.15f, 0.15f);
    private static final float GLOBAL_EVENT_INTERVAL = 45.0f;
    private static final float GLOBAL_EVENT_START_DELAY = 10.0f;
    private static final float PROXIMITY_JUMPSCARE_SLOW_MULTIPLIER = 0.2f;
    private static final float ENEMY_AUDIO_DISTANCE = 18.0f;
    private static final float ENEMY_AUDIO_MIN_VOLUME = 0.1f;
    private static final float ENEMY_AUDIO_MAX_VOLUME = 0.9f;
    private boolean proximityJumpscareActive = false;
    private float proximityJumpscareTimer = 0f;
    private float proximityJumpscareCooldown = 0f;
    private float randomJumpscareTimer = 0f;
    private SpriteBatch jumpscareBatch;
    private Texture jumpscareBloodTexture;
    private Texture jumpscareMonsterTexture;
    private Sound jumpscareSound;
    private final Matrix4 jumpscareProjection = new Matrix4();

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
        if (!Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
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
        jumpscareSound = ResourceManager.getInstance().getSound("jumpscare");
        if (jumpscareSound == null) {
            jumpscareSound = loadOptionalSound("audio/Jumpscare Sound Effect.mp3");
        }
        demonicLaughterSound = ResourceManager.getInstance().getSound("demonic_laughter");
        if (demonicLaughterSound == null) {
            demonicLaughterSound = loadOptionalSound(
                "audio/SCARY DEMONIC LAUGHTER  Horror Sound Effects  - FREE TO USE.mp3"
            );
        }
        enemyProximitySound = ResourceManager.getInstance().getSound("sahur_proximity");
        if (enemyProximitySound == null) {
            enemyProximitySound = loadOptionalSound("audio/sahur.wav");
        }

        jumpscareBloodTexture = loadOptionalTexture("img/Bloedbad.png");
        jumpscareMonsterTexture = loadOptionalTexture("img/Engmonster.png");
        jumpscareBatch = new SpriteBatch();

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
        if (levelLoadingActive) {
            renderLevelLoading(delta);
            return;
        }

        // Handle jumpscare sequence
        if (jumpscareActive) {
            handleJumpscare(delta);
            return;
        }

        // Check for death condition
        if (!isDead) {
            survivalTime += delta;
            checkDeathCondition();
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
            updateGlobalEventScheduler(delta);
            updateProximityJumpscare(delta);
            updateDemonicLaughter(delta);
            updateRandomFlashlightFlicker(delta);
            updateRedFlicker(delta);
            handleInput(delta);
            player.update(delta, maze);
            if (!useNetworkEnemy) {
                enemy.update(delta);
            }
            updateEnemyProximityAudio();
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
            mazeRenderer.updatePortalPlacement(exitPosition);
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

        if (proximityJumpscareActive) {
            renderProximityJumpscareOverlay();
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
        final Vector3 forward = getForwardVector(forwardVector);
        final Vector3 right = getRightVector(rightVector);
        final Vector3 moveDirection = movementInput.setZero();

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
                collisionTestPosition.set(moveDirection).nor().scl(currentSpeed * delta)
                    .add(player.getPosition());
                if (!checkCollision(collisionTestPosition)) {
                    player.getPosition().set(collisionTestPosition);
                }
            }
            return;
        }

        final float moveLen2 = moveDirection.len2();
        if (moveLen2 > 0f) {
            // Calculate speed based on energy level
            final float speedMultiplier = player.getCurrentSpeedMultiplier();
            final float currentSpeed = GameConfig.PLAYER_MOVE_SPEED * speedMultiplier;
            final float moveScale = currentSpeed * delta / (float) Math.sqrt(moveLen2);

            moveDirection.scl(moveScale);

            // Try full movement first
            collisionTestPosition.set(player.getPosition()).add(moveDirection);

            if (!checkCollision(collisionTestPosition)) {
                // No collision, move freely
                player.getPosition().set(collisionTestPosition);
            } else {
                // Try sliding along walls (X direction only)
                collisionTestPosition.set(player.getPosition()).add(moveDirection.x, 0, 0);
                if (!checkCollision(collisionTestPosition)) {
                    player.getPosition().set(collisionTestPosition);
                } else {
                    // Try sliding along walls (Z direction only)
                    collisionTestPosition.set(player.getPosition()).add(0, 0, moveDirection.z);
                    if (!checkCollision(collisionTestPosition)) {
                        player.getPosition().set(collisionTestPosition);
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

        // Debug: trigger demonic laughter
        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) {
            triggerDemonicLaughter();
        }

        // Debug: trigger random flashlight flicker
        if (Gdx.input.isKeyJustPressed(Input.Keys.K)) {
            triggerRandomFlashlightFlicker();
        }

        // Debug: trigger red flicker
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            triggerRedFlicker();
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

    private Texture loadOptionalTexture(final String path) {
        try {
            final FileHandle handle = Gdx.files.internal(path);
            if (handle.exists()) {
                return new Texture(handle);
            }
        } catch (Exception e) {
            System.err.println("[GameScreen] Failed to load texture " + path + ": " + e.getMessage());
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
            networkSyncTarget.set(self.x, self.y, self.z);
            final float dist = player.getPosition().dst(networkSyncTarget);
            if (dist > 1.0f) {
                // Large correction, snap
                player.getPosition().set(networkSyncTarget);
            } else {
                // Smooth blend
                player.getPosition().lerp(networkSyncTarget, 0.1f);
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
    private Vector3 getForwardVector(final Vector3 out) {
        final double radians = Math.toRadians(yaw);
        return out.set(
            (float) Math.sin(radians),
            0,
            -(float) Math.cos(radians)
        );
    }

    /**
     * Gets the right direction vector based on yaw.
     */
    private Vector3 getRightVector(final Vector3 out) {
        final double radians = Math.toRadians(yaw + 90);
        return out.set(
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
        enemy.setSpeedMultiplier(1.0f);
        proximityJumpscareActive = false;
        proximityJumpscareTimer = 0f;

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
        // Only dispose materialManager if we created it ourselves (not from ResourceManager)
        if (materialManager != ResourceManager.getInstance().getMaterialManager()) {
            materialManager.dispose();
        }
        lightingManager.dispose();
        // Only dispose flashlight sound if we loaded it ourselves (not from ResourceManager)
        Sound preloadedSound = ResourceManager.getInstance().getSound("flashlight_toggle");
        if (flashlightToggleSound != null && flashlightToggleSound != preloadedSound) {
            flashlightToggleSound.dispose();
        }
        stopEnemyProximityAudio();
        Sound preloadedEnemySound = ResourceManager.getInstance().getSound("sahur_proximity");
        if (enemyProximitySound != null && enemyProximitySound != preloadedEnemySound) {
            enemyProximitySound.dispose();
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
        stopRandomFlashlightFlicker();
        stopDemonicLaughter();
        if (demonicLaughterSound != null
            && demonicLaughterSound != ResourceManager.getInstance().getSound("demonic_laughter")) {
            demonicLaughterSound.dispose();
        }
        if (jumpscareSound != null && jumpscareSound != ResourceManager.getInstance().getSound("jumpscare")) {
            jumpscareSound.dispose();
        }
        if (loadingBatch != null) {
            loadingBatch.dispose();
            loadingBatch = null;
        }
        if (loadingShapeRenderer != null) {
            loadingShapeRenderer.dispose();
            loadingShapeRenderer = null;
        }
        if (loadingFont != null) {
            loadingFont.dispose();
            loadingFont = null;
        }
        if (loadingSplashImage != null) {
            loadingSplashImage.dispose();
            loadingSplashImage = null;
        }
        if (jumpscareBatch != null) {
            jumpscareBatch.dispose();
            jumpscareBatch = null;
        }
        if (jumpscareBloodTexture != null) {
            jumpscareBloodTexture.dispose();
            jumpscareBloodTexture = null;
        }
        if (jumpscareMonsterTexture != null) {
            jumpscareMonsterTexture.dispose();
            jumpscareMonsterTexture = null;
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
        requestLevelChange(newLevel, newSeed, exitX, exitZ, spawnX, spawnZ, false);
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
            returnToMenuAfterWin();
        }
    }

    private void returnToMenuAfterWin() {
        if (returnToMenuQueued) {
            return;
        }
        returnToMenuQueued = true;
        System.out.println("[GameScreen] Level gehaald, terug naar menu");
        Gdx.app.postRunnable(() -> {
            GameApp.switchScreen("Menu");
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        });
    }

    private void handleSingleplayerLevelTransition() {
        currentLevel = 1;
        System.out.println("[GameScreen] Singleplayer level transition to " + currentLevel);

        // Nieuwe seed
        final long newSeed = System.currentTimeMillis() ^ currentLevel;

        // Simuleer server level change
        requestLevelChange(currentLevel, newSeed, 0, 0, 12f, 12f, true);
    }

    private void updateEnemyProximityAudio() {
        if (enemyProximitySound == null || isDead || jumpscareActive) {
            stopEnemyProximityAudio();
            return;
        }

        final float distance = player.getPosition().dst(enemy.getPosition());
        if (distance <= ENEMY_AUDIO_DISTANCE) {
            final float proximity = Math.max(0f, 1f - (distance / ENEMY_AUDIO_DISTANCE));
            final float volume = ENEMY_AUDIO_MIN_VOLUME
                + (ENEMY_AUDIO_MAX_VOLUME - ENEMY_AUDIO_MIN_VOLUME) * proximity;

            if (enemyProximitySoundId == -1L) {
                enemyProximitySoundId = enemyProximitySound.loop(volume);
            } else {
                enemyProximitySound.setVolume(enemyProximitySoundId, volume);
            }
        } else {
            stopEnemyProximityAudio();
        }
    }

    private void stopEnemyProximityAudio() {
        if (enemyProximitySound != null && enemyProximitySoundId != -1L) {
            enemyProximitySound.stop(enemyProximitySoundId);
            enemyProximitySoundId = -1L;
        }
    }

    private void updateProximityJumpscare(final float delta) {
        if (proximityJumpscareCooldown > 0f) {
            proximityJumpscareCooldown = Math.max(0f, proximityJumpscareCooldown - delta);
        }

        if (proximityJumpscareActive) {
            proximityJumpscareTimer += delta;
            if (proximityJumpscareTimer >= PROXIMITY_JUMPSCARE_DURATION) {
                proximityJumpscareActive = false;
                proximityJumpscareTimer = 0f;
                proximityJumpscareCooldown = PROXIMITY_JUMPSCARE_COOLDOWN;
                enemy.setSpeedMultiplier(1.0f);
            }
            return;
        }

        if (proximityJumpscareCooldown > 0f || jumpscareActive || isDead) {
            return;
        }

        randomJumpscareTimer += delta;
        if (randomJumpscareTimer >= RANDOM_JUMPSCARE_INTERVAL) {
            randomJumpscareTimer = 0f;
            if (Math.random() < RANDOM_JUMPSCARE_CHANCE) {
                triggerProximityJumpscare();
                return;
            }
        }

        final float distance = player.getPosition().dst(enemy.getPosition());
        if (distance <= PROXIMITY_JUMPSCARE_DISTANCE && distance > enemy.getCatchRadius()) {
            triggerProximityJumpscare();
        }
    }

    private void updateDemonicLaughter(final float delta) {
        if (isDead) {
            stopDemonicLaughter();
            return;
        }

        if (demonicLaughterActive) {
            demonicLaughterTimer += delta;
            demonicLaughterFlashToggleTimer += delta;

            if (demonicLaughterTimer < DEMONIC_LAUGHTER_FLICKER_DURATION) {
                if (demonicLaughterFlashToggleTimer >= DEMONIC_LAUGHTER_FLICKER_INTERVAL) {
                    demonicLaughterFlashToggleTimer = 0f;
                }
                final boolean flashlightOn = (int) (demonicLaughterTimer
                    / DEMONIC_LAUGHTER_FLICKER_INTERVAL) % 2 == 0;
                lightingManager.setFlashlightSuppressed(!flashlightOn);
            } else if (demonicLaughterTimer
                < DEMONIC_LAUGHTER_FLICKER_DURATION + DEMONIC_LAUGHTER_BLACKOUT_DURATION) {
                lightingManager.setFlashlightSuppressed(true);
            } else {
                lightingManager.setFlashlightSuppressed(false);
            }

            if (demonicLaughterTimer >= DEMONIC_LAUGHTER_DURATION) {
                stopDemonicLaughter();
            }
            return;
        }

        if (jumpscareActive || proximityJumpscareActive) {
            return;
        }

        demonicLaughterIntervalTimer += delta;
        if (demonicLaughterIntervalTimer >= DEMONIC_LAUGHTER_CHECK_INTERVAL) {
            demonicLaughterIntervalTimer = 0f;
            if (Math.random() < DEMONIC_LAUGHTER_CHANCE_PER_CHECK) {
                triggerDemonicLaughter();
            }
        }
    }

    private void updateGlobalEventScheduler(final float delta) {
        if (isDead) {
            return;
        }

        if (globalEventStartDelayTimer < GLOBAL_EVENT_START_DELAY) {
            globalEventStartDelayTimer += delta;
            return;
        }

        globalEventTimer += delta;
        if (globalEventTimer < GLOBAL_EVENT_INTERVAL) {
            return;
        }

        if (!canTriggerGlobalEvent()) {
            return;
        }

        triggerRandomGlobalEvent();
        globalEventTimer = 0f;
    }

    private boolean canTriggerGlobalEvent() {
        return !jumpscareActive
            && !proximityJumpscareActive
            && !demonicLaughterActive
            && !randomFlickerActive
            && !redFlickerActive;
    }

    private void triggerRandomGlobalEvent() {
        final int choice = (int) (Math.random() * 4);
        switch (choice) {
            case 0:
                triggerProximityJumpscare();
                break;
            case 1:
                triggerDemonicLaughter();
                break;
            case 2:
                triggerRandomFlashlightFlicker();
                break;
            case 3:
            default:
                triggerRedFlicker();
                break;
        }
    }

    private void markEventTriggered() {
        globalEventTimer = 0f;
    }

    private void triggerDemonicLaughter() {
        if (demonicLaughterSound == null || demonicLaughterActive) {
            return;
        }
        demonicLaughterSoundId = demonicLaughterSound.play(1.0f);
        demonicLaughterActive = true;
        demonicLaughterTimer = 0f;
        demonicLaughterFlashToggleTimer = 0f;
        demonicLaughterIntervalTimer = 0f;
        markEventTriggered();
    }

    private void stopDemonicLaughter() {
        lightingManager.setFlashlightSuppressed(false);
        if (demonicLaughterSound != null) {
            if (demonicLaughterSoundId != -1L) {
                demonicLaughterSound.stop(demonicLaughterSoundId);
            } else {
                demonicLaughterSound.stop();
            }
        }
        demonicLaughterSoundId = -1L;
        demonicLaughterActive = false;
        demonicLaughterTimer = 0f;
        demonicLaughterFlashToggleTimer = 0f;
    }

    private void updateRandomFlashlightFlicker(final float delta) {
        if (isDead) {
            stopRandomFlashlightFlicker();
            return;
        }

        if (randomFlickerActive) {
            randomFlickerTimer += delta;
            final boolean flashlightOn = (int) (randomFlickerTimer / RANDOM_FLICKER_INTERVAL) % 2 == 0;
            lightingManager.setFlashlightSuppressed(!flashlightOn);
            if (randomFlickerTimer >= RANDOM_FLICKER_DURATION) {
                stopRandomFlashlightFlicker();
            }
            return;
        }

        if (jumpscareActive || proximityJumpscareActive || demonicLaughterActive) {
            return;
        }

        randomFlickerCheckTimer += delta;
        if (randomFlickerCheckTimer >= RANDOM_FLICKER_CHECK_INTERVAL) {
            randomFlickerCheckTimer = 0f;
            if (Math.random() < RANDOM_FLICKER_CHANCE_PER_CHECK) {
                triggerRandomFlashlightFlicker();
            }
        }
    }

    private void triggerRandomFlashlightFlicker() {
        if (randomFlickerActive) {
            return;
        }
        randomFlickerActive = true;
        randomFlickerTimer = 0f;
        randomFlickerCheckTimer = 0f;
        markEventTriggered();
    }

    private void stopRandomFlashlightFlicker() {
        lightingManager.setFlashlightSuppressed(false);
        randomFlickerActive = false;
        randomFlickerTimer = 0f;
    }

    private void updateRedFlicker(final float delta) {
        if (isDead) {
            stopRedFlicker();
            return;
        }

        if (redFlickerStartDelayTimer < RED_FLICKER_START_DELAY) {
            redFlickerStartDelayTimer += delta;
            return;
        }

        if (redFlickerActive) {
            redFlickerTimer += delta;
            final boolean bright = (int) (redFlickerTimer / RED_FLICKER_INTERVAL) % 2 == 0;
            if (bright) {
                lightingManager.setFlashlightColor(RED_FLICKER_COLOR_BRIGHT);
                mazeRenderer.setLampColorOverride(RED_FLICKER_COLOR_BRIGHT);
            } else {
                lightingManager.setFlashlightColor(RED_FLICKER_COLOR_DIM);
                mazeRenderer.setLampColorOverride(RED_FLICKER_COLOR_DIM);
            }
            if (redFlickerTimer >= RED_FLICKER_DURATION) {
                stopRedFlicker();
                redFlickerWindowTimer = 0f;
                redFlickerTriggerAt = (float) (Math.random() * RED_FLICKER_WINDOW);
                redFlickerScheduled = true;
            }
            return;
        }

        if (jumpscareActive || proximityJumpscareActive || demonicLaughterActive || randomFlickerActive) {
            return;
        }

        redFlickerWindowTimer += delta;
        if (!redFlickerScheduled) {
            redFlickerTriggerAt = (float) (Math.random() * RED_FLICKER_WINDOW);
            redFlickerScheduled = true;
        }

        if (redFlickerWindowTimer >= redFlickerTriggerAt || redFlickerWindowTimer >= RED_FLICKER_WINDOW) {
            triggerRedFlicker();
        }
    }

    private void triggerRedFlicker() {
        if (redFlickerActive) {
            return;
        }
        redFlickerActive = true;
        redFlickerTimer = 0f;
        redFlickerScheduled = false;
        redFlickerWindowTimer = 0f;
        markEventTriggered();
    }

    private void stopRedFlicker() {
        redFlickerActive = false;
        redFlickerTimer = 0f;
        lightingManager.resetFlashlightColor();
        mazeRenderer.setLampColorOverride(null);
    }

    private void triggerProximityJumpscare() {
        proximityJumpscareActive = true;
        proximityJumpscareTimer = 0f;
        randomJumpscareTimer = 0f;
        if (jumpscareSound != null) {
            jumpscareSound.play(1.0f);
        }
        enemy.setSpeedMultiplier(PROXIMITY_JUMPSCARE_SLOW_MULTIPLIER);
        markEventTriggered();
    }

    private void renderProximityJumpscareOverlay() {
        if (jumpscareBatch == null) {
            return;
        }
        final int screenWidth = Gdx.graphics.getBackBufferWidth();
        final int screenHeight = Gdx.graphics.getBackBufferHeight();
        jumpscareProjection.setToOrtho2D(0, 0, screenWidth, screenHeight);
        jumpscareBatch.setProjectionMatrix(jumpscareProjection);

        final float fadeIn = Math.min(1.0f, proximityJumpscareTimer / 0.1f);
        final float fadeOutStart = PROXIMITY_JUMPSCARE_DURATION - 0.2f;
        final float fadeOut = proximityJumpscareTimer > fadeOutStart
            ? Math.max(0.0f, (PROXIMITY_JUMPSCARE_DURATION - proximityJumpscareTimer) / 0.2f)
            : 1.0f;
        final float alpha = Math.min(fadeIn, fadeOut);

        jumpscareBatch.begin();
        if (jumpscareBloodTexture != null) {
            jumpscareBatch.setColor(1f, 1f, 1f, alpha);
            jumpscareBatch.draw(jumpscareBloodTexture, 0, 0, screenWidth, screenHeight);
        }
        if (jumpscareMonsterTexture != null) {
            final float scale = Math.min(
                (float) screenWidth / jumpscareMonsterTexture.getWidth(),
                (float) screenHeight / jumpscareMonsterTexture.getHeight()
            ) * 1.2f;
            final float drawWidth = jumpscareMonsterTexture.getWidth() * scale;
            final float drawHeight = jumpscareMonsterTexture.getHeight() * scale;
            final float drawX = (screenWidth - drawWidth) / 2f;
            final float drawY = (screenHeight - drawHeight) / 2f;
            jumpscareBatch.setColor(1f, 1f, 1f, Math.min(1f, alpha + 0.1f));
            jumpscareBatch.draw(jumpscareMonsterTexture, drawX, drawY, drawWidth, drawHeight);
        }
        jumpscareBatch.setColor(1f, 1f, 1f, 1f);
        jumpscareBatch.end();
    }

    private void requestLevelChange(final int newLevel, final long newSeed,
                                    final float exitX, final float exitZ,
                                    final float spawnX, final float spawnZ,
                                    final boolean recalcExit) {
        pendingLevelChange = new PendingLevelChange(
            newLevel,
            newSeed,
            exitX,
            exitZ,
            spawnX,
            spawnZ,
            recalcExit
        );
        levelLoadingActive = true;
        levelChangeApplied = false;
        levelLoadingTimer = 0f;
        ensureLoadingResources();
    }

    private void applyLevelChange(final PendingLevelChange change) {
        System.out.println("[GameScreen] ====== LEVEL CHANGE TO LEVEL " + change.newLevel + " ======");

        currentLevel = change.newLevel;

        // Vernietig oude maze renderer
        if (mazeRenderer != null) {
            mazeRenderer.dispose();
        }

        // Genereer nieuwe maze
        maze = new Maze(GameConfig.MAZE_SIZE, GameConfig.MAZE_SIZE, change.newSeed);
        maze.generate();

        // Herbouw renderer
        mazeRenderer = new MazeRenderer(maze, materialManager, lightingManager);
        mazeRenderer.initialize();

        // Herlaad photo frames en boosts voor nieuwe level
        photoFrames = createPhotoFramesOnWalls();
        mazeRenderer.loadPhotoFrames(photoFrames);

        boosts = createBoostPickups();
        mazeRenderer.loadBoosts(boosts);

        // Update exit positie
        exitPosition.set(change.exitX, GameConfig.PLAYER_HEIGHT, change.exitZ);


        // Reset speler positie
        player.getPosition().set(change.spawnX, GameConfig.PLAYER_HEIGHT, change.spawnZ);

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

        if (change.recalcExit) {
            findExitLocation();
        }

        System.out.println("[GameScreen] Level change complete!");
    }

    private void renderLevelLoading(final float delta) {
        levelLoadingTimer += delta;

        if (!levelChangeApplied && levelLoadingTimer >= LEVEL_LOADING_MIN_TIME) {
            applyLevelChange(pendingLevelChange);
            levelChangeApplied = true;
            levelLoadingTimer = 0f;
        } else if (levelChangeApplied && levelLoadingTimer >= LEVEL_LOADING_POST_TIME) {
            levelLoadingActive = false;
            levelChangeApplied = false;
            pendingLevelChange = null;
        }

        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        final int screenWidth = Gdx.graphics.getWidth();
        final int screenHeight = Gdx.graphics.getHeight();

        loadingBatch.begin();
        loadingBatch.draw(loadingSplashImage, 0, 0, screenWidth, screenHeight);
        loadingBatch.end();

        final int barX = (screenWidth - LOADING_BAR_WIDTH) / 2;
        final int barY = LOADING_BAR_BOTTOM_MARGIN;
        final float progress = levelChangeApplied ? 1.0f : 0.4f;

        loadingShapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        loadingShapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1.0f);
        loadingShapeRenderer.rect(barX, barY, LOADING_BAR_WIDTH, LOADING_BAR_HEIGHT);
        loadingShapeRenderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        loadingShapeRenderer.rect(barX, barY, LOADING_BAR_WIDTH * progress, LOADING_BAR_HEIGHT);
        loadingShapeRenderer.end();

        loadingBatch.begin();
        loadingFont.setColor(1f, 1f, 1f, 1f);
        final int textY = barY + LOADING_BAR_HEIGHT + 25;
        loadingFont.draw(loadingBatch, "Loading world...", 0, textY, screenWidth, Align.center, false);
        loadingBatch.end();
    }

    private void ensureLoadingResources() {
        if (loadingBatch != null) {
            return;
        }
        loadingBatch = new SpriteBatch();
        loadingShapeRenderer = new ShapeRenderer();
        loadingFont = new BitmapFont();
        loadingFont.getData().setScale(1.2f);
        loadingSplashImage = new Texture(Gdx.files.internal("img/splash.png"));
    }

    private static final class PendingLevelChange {
        private final int newLevel;
        private final long newSeed;
        private final float exitX;
        private final float exitZ;
        private final float spawnX;
        private final float spawnZ;
        private final boolean recalcExit;

        private PendingLevelChange(final int newLevel, final long newSeed,
                                   final float exitX, final float exitZ,
                                   final float spawnX, final float spawnZ,
                                   final boolean recalcExit) {
            this.newLevel = newLevel;
            this.newSeed = newSeed;
            this.exitX = exitX;
            this.exitZ = exitZ;
            this.spawnX = spawnX;
            this.spawnZ = spawnZ;
            this.recalcExit = recalcExit;
        }
    }
}
