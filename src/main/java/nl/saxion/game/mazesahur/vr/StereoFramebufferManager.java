package nl.saxion.game.mazesahur.vr;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/**
 * Manages framebuffers for stereo VR rendering.
 * Creates separate framebuffers for left and right eyes,
 * handles binding/unbinding, and provides texture handles for VR compositor submission.
 *
 * Rendering Flow:
 * 1. bind(Eye.LEFT) - Bind left eye framebuffer
 * 2. Render scene with left camera
 * 3. unbind()
 * 4. bind(Eye.RIGHT) - Bind right eye framebuffer
 * 5. Render scene with right camera
 * 6. unbind()
 * 7. submit() - Submit both framebuffers to VR compositor
 *
 * @author Tim (VR Conversion)
 * @version 1.0
 */
public class StereoFramebufferManager {

    /**
     * Eye enumeration for clarity in API calls.
     */
    public enum Eye {
        LEFT,
        RIGHT
    }

    private final VRManager vrManager;

    private FrameBuffer leftEyeFramebuffer;
    private FrameBuffer rightEyeFramebuffer;

    private final int renderWidth;
    private final int renderHeight;

    private Eye currentlyBound = null;

    /**
     * Creates stereo framebuffer manager.
     *
     * @param vrManager VR runtime manager (provides recommended render size)
     */
    public StereoFramebufferManager(final VRManager vrManager) {
        this.vrManager = vrManager;

        // Get recommended render size from VR runtime
        this.renderWidth = vrManager.getRenderWidth();
        this.renderHeight = vrManager.getRenderHeight();

        // Create framebuffers
        createFramebuffers();
    }

    /**
     * Creates OpenGL framebuffers for each eye.
     */
    private void createFramebuffers() {
        System.out.println("[StereoFramebufferManager] Creating framebuffers: "
            + renderWidth + "x" + renderHeight + " per eye");

        // Create left eye framebuffer with depth attachment
        leftEyeFramebuffer = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            renderWidth,
            renderHeight,
            true // Has depth buffer
        );

        // Create right eye framebuffer with depth attachment
        rightEyeFramebuffer = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            renderWidth,
            renderHeight,
            true // Has depth buffer
        );

        System.out.println("[StereoFramebufferManager] Framebuffers created successfully");
        System.out.println("[StereoFramebufferManager] Left eye texture ID: "
            + leftEyeFramebuffer.getColorBufferTexture().getTextureObjectHandle());
        System.out.println("[StereoFramebufferManager] Right eye texture ID: "
            + rightEyeFramebuffer.getColorBufferTexture().getTextureObjectHandle());
    }

    /**
     * Binds the specified eye's framebuffer for rendering.
     * Sets viewport to full framebuffer size and clears color and depth.
     *
     * @param eye Which eye to render to
     */
    public void bind(final Eye eye) {
        if (eye == Eye.LEFT) {
            leftEyeFramebuffer.begin();
            currentlyBound = Eye.LEFT;
        } else {
            rightEyeFramebuffer.begin();
            currentlyBound = Eye.RIGHT;
        }

        // Set viewport to full framebuffer size
        Gdx.gl.glViewport(0, 0, renderWidth, renderHeight);

        // Clear framebuffer
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Unbinds the currently bound framebuffer.
     * Restores default framebuffer and viewport.
     */
    public void unbind() {
        if (currentlyBound == Eye.LEFT) {
            leftEyeFramebuffer.end();
        } else if (currentlyBound == Eye.RIGHT) {
            rightEyeFramebuffer.end();
        }

        currentlyBound = null;

        // Restore default viewport (window size)
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /**
     * Submits both eye framebuffers to VR compositor.
     * Call this after rendering both eyes.
     */
    public void submit() {
        final int leftTexture = leftEyeFramebuffer.getColorBufferTexture().getTextureObjectHandle();
        final int rightTexture = rightEyeFramebuffer.getColorBufferTexture().getTextureObjectHandle();

        vrManager.submitFrame(leftTexture, rightTexture);
    }

    /**
     * Renders framebuffers to screen for debugging (side-by-side).
     * This allows seeing VR output on desktop monitor.
     * Not used when actually in VR mode.
     */
    public void renderToScreen() {
        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Get screen dimensions
        final int screenWidth = Gdx.graphics.getWidth();
        final int screenHeight = Gdx.graphics.getHeight();
        final int halfWidth = screenWidth / 2;

        // Disable depth test for simple 2D blit
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // TODO: Implement simple texture blitting for debug visualization
        // For now, the VR compositor handles display
        // This method is primarily for testing without VR headset

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /**
     * Disposes framebuffer resources.
     * Call this on cleanup.
     */
    public void dispose() {
        if (leftEyeFramebuffer != null) {
            leftEyeFramebuffer.dispose();
        }
        if (rightEyeFramebuffer != null) {
            rightEyeFramebuffer.dispose();
        }

        System.out.println("[StereoFramebufferManager] Framebuffers disposed");
    }

    // Getters

    /**
     * @return Left eye framebuffer
     */
    public FrameBuffer getLeftEyeFramebuffer() {
        return leftEyeFramebuffer;
    }

    /**
     * @return Right eye framebuffer
     */
    public FrameBuffer getRightEyeFramebuffer() {
        return rightEyeFramebuffer;
    }

    /**
     * @return Left eye texture for compositor submission
     */
    public Texture getLeftEyeTexture() {
        return leftEyeFramebuffer.getColorBufferTexture();
    }

    /**
     * @return Right eye texture for compositor submission
     */
    public Texture getRightEyeTexture() {
        return rightEyeFramebuffer.getColorBufferTexture();
    }

    /**
     * @return Render width per eye
     */
    public int getRenderWidth() {
        return renderWidth;
    }

    /**
     * @return Render height per eye
     */
    public int getRenderHeight() {
        return renderHeight;
    }

    /**
     * @return Currently bound eye (null if none)
     */
    public Eye getCurrentlyBound() {
        return currentlyBound;
    }
}
