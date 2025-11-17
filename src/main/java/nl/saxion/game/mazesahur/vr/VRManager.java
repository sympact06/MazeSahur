package nl.saxion.game.mazesahur.vr;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.lwjgl.openvr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.FloatBuffer;

/**
 * Manages VR runtime initialization and provides interface to VR headset tracking.
 * Currently supports OpenVR (SteamVR/Oculus Link) for desktop VR testing.
 * Future: Will support OpenXR for Quest 2 native deployment.
 *
 * @author Tim (VR Conversion)
 * @version 1.0
 */
public class VRManager {

    private boolean vrInitialized = false;
    private boolean vrAvailable = false;

    // OpenVR handles
    private IntBuffer hmdErrorStore;

    // Eye render target dimensions (per eye)
    private int renderWidth;
    private int renderHeight;

    // Inter-Pupillary Distance (meters)
    private float ipd = 0.064f; // Default 64mm

    // Headset tracking data
    private final Vector3 headPosition = new Vector3();
    private final Quaternion headRotation = new Quaternion();
    private final Matrix4 headTransform = new Matrix4();

    // Eye projection matrices (calculated from VR runtime)
    private final Matrix4 leftEyeProjection = new Matrix4();
    private final Matrix4 rightEyeProjection = new Matrix4();

    // Eye view matrices (offset from head position)
    private final Matrix4 leftEyeView = new Matrix4();
    private final Matrix4 rightEyeView = new Matrix4();

    /**
     * Initializes the VR runtime (OpenVR).
     *
     * @return true if VR initialized successfully, false otherwise
     */
    public boolean initialize() {
        System.out.println("[VRManager] Initializing OpenVR...");

        try {
            // Try to load OpenVR - will fail on ARM64 macOS or without VR runtime
            try (MemoryStack stack = MemoryStack.stackPush()) {
                hmdErrorStore = stack.mallocInt(1);

                // Check if VR runtime is available
                if (!VR.VR_IsRuntimeInstalled()) {
                    System.err.println("[VRManager] OpenVR runtime not installed!");
                    return false;
                }

                if (!VR.VR_IsHmdPresent()) {
                    System.err.println("[VRManager] No VR headset detected!");
                    return false;
                }

                // Initialize OpenVR
                int token = VR.VR_InitInternal(hmdErrorStore, VR.EVRApplicationType_VRApplication_Scene);
                if (hmdErrorStore.get(0) != VR.EVRInitError_VRInitError_None) {
                    System.err.println("[VRManager] Failed to initialize OpenVR: "
                        + VR.VR_GetVRInitErrorAsEnglishDescription(hmdErrorStore.get(0)));
                    return false;
                }

                OpenVR.create(token);
                System.out.println("[VRManager] OpenVR initialized successfully!");

                // Get recommended render target size
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                VRSystem.VRSystem_GetRecommendedRenderTargetSize(width, height);
                renderWidth = width.get(0);
                renderHeight = height.get(0);

                System.out.println("[VRManager] Recommended render size per eye: "
                    + renderWidth + "x" + renderHeight);

                // Calculate IPD from eye-to-head transforms
                calculateIPD();

                // Initialize projection matrices
                updateProjectionMatrices(0.01f, 100.0f);

                vrInitialized = true;
                vrAvailable = true;
                return true;
            }

        } catch (UnsatisfiedLinkError e) {
            System.err.println("[VRManager] OpenVR native library not available: " + e.getMessage());
            System.err.println("[VRManager] This is expected on ARM64 macOS (Apple Silicon)");
            System.err.println("[VRManager] VR is only supported on x64 Windows/Linux/macOS Intel");
            System.err.println("[VRManager] Falling back to desktop mode");
            return false;
        } catch (Exception e) {
            System.err.println("[VRManager] Exception during VR initialization: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Calculates IPD from the VR runtime's eye-to-head transforms.
     */
    private void calculateIPD() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            HmdMatrix34 leftEyeTransform = HmdMatrix34.malloc(stack);
            HmdMatrix34 rightEyeTransform = HmdMatrix34.malloc(stack);

            VRSystem.VRSystem_GetEyeToHeadTransform(VR.EVREye_Eye_Left, leftEyeTransform);
            VRSystem.VRSystem_GetEyeToHeadTransform(VR.EVREye_Eye_Right, rightEyeTransform);

            float leftX = leftEyeTransform.m(3); // Column-major: m[12] = m(3)
            float rightX = rightEyeTransform.m(3);

            ipd = Math.abs(rightX - leftX);
            System.out.println("[VRManager] Calculated IPD: " + (ipd * 1000.0f) + "mm");
        }
    }

    /**
     * Updates projection matrices from VR runtime.
     *
     * @param nearClip Near clipping plane
     * @param farClip Far clipping plane
     */
    private void updateProjectionMatrices(final float nearClip, final float farClip) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get projection matrices from OpenVR
            HmdMatrix44 leftProj = HmdMatrix44.malloc(stack);
            HmdMatrix44 rightProj = HmdMatrix44.malloc(stack);

            VRSystem.VRSystem_GetProjectionMatrix(VR.EVREye_Eye_Left, nearClip, farClip, leftProj);
            VRSystem.VRSystem_GetProjectionMatrix(VR.EVREye_Eye_Right, nearClip, farClip, rightProj);

            // Convert to libGDX Matrix4
            convertHmdMatrix44ToMatrix4(leftProj, leftEyeProjection);
            convertHmdMatrix44ToMatrix4(rightProj, rightEyeProjection);
        }
    }

    /**
     * Updates headset tracking (position and rotation).
     * Should be called every frame.
     */
    public void updateTracking() {
        if (!vrInitialized) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            TrackedDevicePose.Buffer trackedDevicePoses = TrackedDevicePose.malloc(
                VR.k_unMaxTrackedDeviceCount, stack);

            // Get current poses
            VRCompositor.VRCompositor_WaitGetPoses(trackedDevicePoses, null);

            // HMD is always device 0
            TrackedDevicePose hmdPose = trackedDevicePoses.get(VR.k_unTrackedDeviceIndex_Hmd);

            if (hmdPose.bPoseIsValid()) {
                HmdMatrix34 mat = hmdPose.mDeviceToAbsoluteTracking();

                // Extract position (4th column) - column-major indexing
                headPosition.set(
                    mat.m(3),  // m[12]
                    mat.m(7),  // m[13]
                    mat.m(11)  // m[14]
                );

                // Extract rotation matrix and convert to quaternion
                convertHmdMatrix34ToMatrix4(mat, headTransform);
                headTransform.getRotation(headRotation);

                // Update eye view matrices (offset from head by IPD)
                updateEyeViewMatrices();
            }
        }
    }

    /**
     * Updates view matrices for left and right eyes based on head transform.
     */
    private void updateEyeViewMatrices() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get eye-to-head transforms from OpenVR
            HmdMatrix34 leftEyeTransform = HmdMatrix34.malloc(stack);
            HmdMatrix34 rightEyeTransform = HmdMatrix34.malloc(stack);

            VRSystem.VRSystem_GetEyeToHeadTransform(VR.EVREye_Eye_Left, leftEyeTransform);
            VRSystem.VRSystem_GetEyeToHeadTransform(VR.EVREye_Eye_Right, rightEyeTransform);

            Matrix4 leftOffset = new Matrix4();
            Matrix4 rightOffset = new Matrix4();
            convertHmdMatrix34ToMatrix4(leftEyeTransform, leftOffset);
            convertHmdMatrix34ToMatrix4(rightEyeTransform, rightOffset);

            // Combine head transform with eye offsets
            leftEyeView.set(headTransform).mul(leftOffset).inv();
            rightEyeView.set(headTransform).mul(rightOffset).inv();
        }
    }

    /**
     * Converts OpenVR HmdMatrix34 to libGDX Matrix4.
     * HmdMatrix34 is column-major: m[0-2]=col0, m[3-5]=col1, m[6-8]=col2, m[9-11]=col3
     *
     * @param hmdMat OpenVR matrix (3x4)
     * @param gdxMat Output libGDX matrix (4x4)
     */
    private void convertHmdMatrix34ToMatrix4(final HmdMatrix34 hmdMat, final Matrix4 gdxMat) {
        float[] values = {
            hmdMat.m(0), hmdMat.m(1), hmdMat.m(2), 0.0f,
            hmdMat.m(3), hmdMat.m(4), hmdMat.m(5), 0.0f,
            hmdMat.m(6), hmdMat.m(7), hmdMat.m(8), 0.0f,
            hmdMat.m(9), hmdMat.m(10), hmdMat.m(11), 1.0f
        };
        gdxMat.set(values);
    }

    /**
     * Converts OpenVR HmdMatrix44 to libGDX Matrix4.
     * Both are column-major, so direct copy.
     *
     * @param hmdMat OpenVR matrix (4x4)
     * @param gdxMat Output libGDX matrix (4x4)
     */
    private void convertHmdMatrix44ToMatrix4(final HmdMatrix44 hmdMat, final Matrix4 gdxMat) {
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) {
            values[i] = hmdMat.m(i);
        }
        gdxMat.set(values);
    }

    /**
     * Submits rendered textures to VR compositor.
     * This is a placeholder - actual implementation will use framebuffer textures.
     *
     * @param leftEyeTexture OpenGL texture ID for left eye
     * @param rightEyeTexture OpenGL texture ID for right eye
     */
    public void submitFrame(final int leftEyeTexture, final int rightEyeTexture) {
        if (!vrInitialized) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Texture leftTex = Texture.malloc(stack);
            leftTex.set(leftEyeTexture, VR.ETextureType_TextureType_OpenGL,
                VR.EColorSpace_ColorSpace_Auto);

            Texture rightTex = Texture.malloc(stack);
            rightTex.set(rightEyeTexture, VR.ETextureType_TextureType_OpenGL,
                VR.EColorSpace_ColorSpace_Auto);

            int errorLeft = VRCompositor.VRCompositor_Submit(
                VR.EVREye_Eye_Left, leftTex, null, VR.EVRSubmitFlags_Submit_Default);
            int errorRight = VRCompositor.VRCompositor_Submit(
                VR.EVREye_Eye_Right, rightTex, null, VR.EVRSubmitFlags_Submit_Default);

            if (errorLeft != VR.EVRCompositorError_VRCompositorError_None
                || errorRight != VR.EVRCompositorError_VRCompositorError_None) {
                System.err.println("[VRManager] Compositor submit error: L="
                    + errorLeft + " R=" + errorRight);
            }
        }
    }

    /**
     * Shuts down VR runtime.
     */
    public void shutdown() {
        if (vrInitialized) {
            System.out.println("[VRManager] Shutting down OpenVR...");
            VR.VR_ShutdownInternal();
            vrInitialized = false;
            vrAvailable = false;
        }
    }

    // Getters

    /**
     * @return true if VR is initialized and available
     */
    public boolean isVRAvailable() {
        return vrAvailable;
    }

    /**
     * @return Recommended render width per eye
     */
    public int getRenderWidth() {
        return renderWidth;
    }

    /**
     * @return Recommended render height per eye
     */
    public int getRenderHeight() {
        return renderHeight;
    }

    /**
     * @return Inter-Pupillary Distance in meters
     */
    public float getIPD() {
        return ipd;
    }

    /**
     * @return Current head position in VR space
     */
    public Vector3 getHeadPosition() {
        return new Vector3(headPosition);
    }

    /**
     * @return Current head rotation as quaternion
     */
    public Quaternion getHeadRotation() {
        return new Quaternion(headRotation);
    }

    /**
     * @return Head transform matrix (position + rotation)
     */
    public Matrix4 getHeadTransform() {
        return new Matrix4(headTransform);
    }

    /**
     * @return Left eye projection matrix
     */
    public Matrix4 getLeftEyeProjection() {
        return new Matrix4(leftEyeProjection);
    }

    /**
     * @return Right eye projection matrix
     */
    public Matrix4 getRightEyeProjection() {
        return new Matrix4(rightEyeProjection);
    }

    /**
     * @return Left eye view matrix
     */
    public Matrix4 getLeftEyeView() {
        return new Matrix4(leftEyeView);
    }

    /**
     * @return Right eye view matrix
     */
    public Matrix4 getRightEyeView() {
        return new Matrix4(rightEyeView);
    }
}
