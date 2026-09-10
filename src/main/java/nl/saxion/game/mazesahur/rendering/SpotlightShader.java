package nl.saxion.game.mazesahur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Custom shader for realistic spotlight rendering.
 * Implements proper cone-based spotlight with distance attenuation and smooth falloff.
 *
 * @author Olivier, Luuk, Russell, Tim
 * @version 1.0
 */
public class SpotlightShader implements Shader {
    // Custom texture attribute types for PBR textures
    // Reflection is repurposed for roughness maps, Emissive for specular maps
    private static final long ROUGHNESS_TEXTURE = com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection;
    private static final long SPECULAR_TEXTURE = com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive;

    private ShaderProgram program;
    private Camera camera;
    private RenderContext context;
    private final Matrix3 reusableNormalMatrix = new Matrix3();
    private final float[] reusableNormalValues = new float[9];

    // Spotlight parameters
    private final Vector3 spotPosition = new Vector3();
    private final Vector3 spotDirection = new Vector3(0, 0, -1);
    private final Vector3 spotColor = new Vector3(1.4f, 1.4f, 1.35f);
    private float spotIntensity = 1.5f;
    private float spotCutoff = (float) Math.cos(Math.toRadians(25.0)); // Inner cone angle
    private float spotOuterCutoff = (float) Math.cos(Math.toRadians(35.0)); // Outer cone angle
    private float spotAttenuation = 0.015f;
    private boolean enabled = true;

    // Ambient and moonlight (slightly increased for better visibility with ceiling lamps)
    private final Vector3 ambientLight = new Vector3(0.002f, 0.002f, 0.0025f);
    private final Vector3 moonDirection = new Vector3(-0.2f, -1f, -0.3f);
    private final Vector3 moonColor = new Vector3(0.005f, 0.006f, 0.007f);

    // Fog parameters for horror atmosphere
    private final Vector3 fogColor = new Vector3(0.0f, 0.0f, 0.0f); // Pure black fog
    private float fogDensity = 0.12f;     // How thick the fog is
    private float fogStart = 8.0f;        // Distance where fog starts (meters)
    private float fogEnd = 35.0f;         // Distance where fog is maximum

    // Parallax occlusion mapping parameters
    private float heightScale = 0.06f;    // Depth scale for parallax effect (conservative but visible)

    // Point lights for ceiling lamps
    private int numPointLights = 0;
    private final Vector3[] pointLightPositions = new Vector3[20];
    private final Vector3[] pointLightColors = new Vector3[20];
    private final float[] pointLightIntensities = new float[20];

    public SpotlightShader() {
        // Initialize point light arrays
        for (int i = 0; i < 20; i++) {
            pointLightPositions[i] = new Vector3();
            pointLightColors[i] = new Vector3(1.0f, 0.9f, 0.7f); // Warm light color
            pointLightIntensities[i] = 1.0f;
        }

        // Enable shader precompilation hints for better performance on Metal/Mac
        ShaderProgram.prependVertexCode = "#ifdef GL_ES\nprecision mediump float;\n#endif\n";
        ShaderProgram.prependFragmentCode = "#ifdef GL_ES\nprecision mediump float;\n#endif\n";

        final String vertexShader = Gdx.files.internal("shaders/spotlight.vertex.glsl").readString();
        final String fragmentShader = Gdx.files.internal("shaders/spotlight.fragment.glsl").readString();

        program = new ShaderProgram(vertexShader, fragmentShader);

        if (!program.isCompiled()) {
            throw new GdxRuntimeException("Shader compilation failed:\n" + program.getLog());
        }

        // AGGRESSIVE shader warmup for potato PCs - force GPU compilation at startup
        System.out.println("[SpotlightShader] Precompiling shader (may take a moment on low-end systems)...");
        program.bind();

        // Set ALL uniforms to force complete GPU compilation
        program.setUniformf("u_spotIntensity", 0.0f);
        program.setUniformf("u_heightScale", 0.06f);
        program.setUniformf("u_hasHeightMap", 0.0f);
        program.setUniformf("u_hasRoughnessMap", 0.0f);
        program.setUniformf("u_hasSpecularMap", 0.0f);
        program.setUniformf("u_hasEmissiveMap", 0.0f);
        program.setUniformi("u_numPointLights", 0);
        program.setUniformf("u_fogDensity", 0.12f);

        System.out.println("[SpotlightShader] Shader fully precompiled and ready for optimal performance");
    }

    @Override
    public void init() {
        // Shader is initialized in constructor
    }

    @Override
    public int compareTo(Shader other) {
        return 0;
    }

    @Override
    public boolean canRender(Renderable instance) {
        return true;
    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        this.camera = camera;
        this.context = context;
        program.bind();

        // Set camera uniforms
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformf("u_cameraPosition", camera.position);

        // Set ambient and moonlight
        program.setUniformf("u_ambientLight", ambientLight);
        program.setUniformf("u_moonDirection", moonDirection.nor());
        program.setUniformf("u_moonColor", moonColor);

        // Set fog parameters
        program.setUniformf("u_fogColor", fogColor);
        program.setUniformf("u_fogDensity", fogDensity);
        program.setUniformf("u_fogStart", fogStart);
        program.setUniformf("u_fogEnd", fogEnd);

        // Set parallax occlusion mapping parameters
        program.setUniformf("u_heightScale", heightScale);

        // Set spotlight parameters
        if (enabled) {
            program.setUniformf("u_spotPosition", spotPosition);
            program.setUniformf("u_spotDirection", spotDirection.nor());
            program.setUniformf("u_spotColor", spotColor);
            program.setUniformf("u_spotIntensity", spotIntensity);
            program.setUniformf("u_spotCutoff", spotCutoff);
            program.setUniformf("u_spotOuterCutoff", spotOuterCutoff);
            program.setUniformf("u_spotAttenuation", spotAttenuation);
        } else {
            // Disable spotlight by setting intensity to 0
            program.setUniformf("u_spotIntensity", 0f);
        }

        // Set point light parameters (only if lights exist)
        program.setUniformi("u_numPointLights", numPointLights);
        if (numPointLights > 0) {
            for (int i = 0; i < numPointLights; i++) {
                try {
                    program.setUniformf("u_pointLightPositions[" + i + "]", pointLightPositions[i]);
                    program.setUniformf("u_pointLightColors[" + i + "]", pointLightColors[i]);
                    program.setUniformf("u_pointLightIntensities[" + i + "]", pointLightIntensities[i]);
                } catch (IllegalArgumentException e) {
                    // Uniform doesn't exist in shader (likely optimized out), skip
                    break;
                }
            }
        }

        // Note: Maze texture uniforms removed - shadow casting disabled for performance

        context.setDepthTest(GL20.GL_LEQUAL);
        context.setCullFace(GL20.GL_BACK);
    }

    @Override
    public void render(Renderable renderable) {
        // Set per-object uniforms
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform);

        // Extract 3x3 normal matrix from 4x4 world transform
        // Normal matrix is the transpose of the inverse of the upper-left 3x3 of world matrix
        final com.badlogic.gdx.math.Matrix4 worldMat = renderable.worldTransform;

        // Extract 3x3 rotation/scale part from 4x4 matrix
        final float[] vals = worldMat.val;
        reusableNormalValues[0] = vals[com.badlogic.gdx.math.Matrix4.M00];
        reusableNormalValues[1] = vals[com.badlogic.gdx.math.Matrix4.M01];
        reusableNormalValues[2] = vals[com.badlogic.gdx.math.Matrix4.M02];
        reusableNormalValues[3] = vals[com.badlogic.gdx.math.Matrix4.M10];
        reusableNormalValues[4] = vals[com.badlogic.gdx.math.Matrix4.M11];
        reusableNormalValues[5] = vals[com.badlogic.gdx.math.Matrix4.M12];
        reusableNormalValues[6] = vals[com.badlogic.gdx.math.Matrix4.M20];
        reusableNormalValues[7] = vals[com.badlogic.gdx.math.Matrix4.M21];
        reusableNormalValues[8] = vals[com.badlogic.gdx.math.Matrix4.M22];
        reusableNormalMatrix.set(reusableNormalValues);

        program.setUniformMatrix("u_normalMatrix", reusableNormalMatrix);

        // Bind textures with proper fallbacks
        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute diffuseAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class,
                com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse);

        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute normalAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class,
                com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal);

        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute heightAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class,
                com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Bump);

        // Get roughness and specular attributes using the class-level constants
        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute roughnessAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class, ROUGHNESS_TEXTURE);

        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute specularAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class, SPECULAR_TEXTURE);

        // Bind diffuse texture
        if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
            program.setUniformi("u_diffuseTexture", unit);
        }

        // Bind normal map (use diffuse as fallback)
        if (normalAttr != null && normalAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(normalAttr.textureDescription.texture);
            program.setUniformi("u_normalTexture", unit);
        } else if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
            program.setUniformi("u_normalTexture", unit);
        }

        // Bind height map and set flag
        if (heightAttr != null && heightAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(heightAttr.textureDescription.texture);
            program.setUniformi("u_heightTexture", unit);
            program.setUniformf("u_hasHeightMap", 1.0f);
        } else {
            // Disable parallax occlusion mapping
            program.setUniformf("u_hasHeightMap", 0.0f);
            // Still bind a texture to prevent shader errors (use diffuse)
            if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
                final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
                program.setUniformi("u_heightTexture", unit);
            }
        }

        // Bind roughness map and set flag
        if (roughnessAttr != null && roughnessAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(roughnessAttr.textureDescription.texture);
            program.setUniformi("u_roughnessTexture", unit);
            program.setUniformf("u_hasRoughnessMap", 1.0f);
        } else {
            // Use default roughness
            program.setUniformf("u_hasRoughnessMap", 0.0f);
            // Still bind a texture to prevent shader errors (use diffuse)
            if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
                final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
                program.setUniformi("u_roughnessTexture", unit);
            }
        }

        // Bind specular map and set flag
        if (specularAttr != null && specularAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(specularAttr.textureDescription.texture);
            program.setUniformi("u_specularTexture", unit);
            program.setUniformf("u_hasSpecularMap", 1.0f);
        } else {
            // Use default specular strength
            program.setUniformf("u_hasSpecularMap", 0.0f);
            // Still bind a texture to prevent shader errors (use diffuse)
            if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
                final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
                program.setUniformi("u_specularTexture", unit);
            }
        }

        // Bind emissive texture and color for self-illumination (lamps)
        final com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute emissiveTextureAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.class,
                com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive);

        final com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute emissiveColorAttr =
            renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.class,
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive);

        if (emissiveTextureAttr != null && emissiveTextureAttr.textureDescription.texture != null) {
            final int unit = context.textureBinder.bind(emissiveTextureAttr.textureDescription.texture);
            program.setUniformi("u_emissiveTexture", unit);
            program.setUniformf("u_hasEmissiveMap", 1.0f);

            // Set emissive color
            if (emissiveColorAttr != null) {
                program.setUniformf("u_emissiveColor",
                    emissiveColorAttr.color.r,
                    emissiveColorAttr.color.g,
                    emissiveColorAttr.color.b);
            } else {
                program.setUniformf("u_emissiveColor", 1.0f, 1.0f, 1.0f);
            }
        } else {
            // No emissive
            program.setUniformf("u_hasEmissiveMap", 0.0f);
            program.setUniformf("u_emissiveColor", 0.0f, 0.0f, 0.0f);
            // Still bind a texture to prevent shader errors (use diffuse)
            if (diffuseAttr != null && diffuseAttr.textureDescription.texture != null) {
                final int unit = context.textureBinder.bind(diffuseAttr.textureDescription.texture);
                program.setUniformi("u_emissiveTexture", unit);
            }
        }

        // Render
        renderable.meshPart.render(program);
    }

    @Override
    public void end() {
        // Cleanup if needed
    }

    @Override
    public void dispose() {
        if (program != null) {
            program.dispose();
        }
    }

    /**
     * Updates the flashlight position and direction.
     */
    public void updateFlashlight(Vector3 position, Vector3 direction, float intensity) {
        this.spotPosition.set(position);
        this.spotDirection.set(direction).nor();
        this.spotIntensity = intensity;
    }

    /**
     * Sets the flashlight color.
     */
    public void setSpotColor(final Vector3 color) {
        this.spotColor.set(color);
    }

    /**
     * Sets whether the flashlight is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether the flashlight is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets fog density (higher = thicker fog).
     * @param density Fog density (typically 0.05 - 0.2)
     */
    public void setFogDensity(float density) {
        this.fogDensity = density;
    }

    /**
     * Sets fog distance range.
     * @param start Distance where fog begins
     * @param end Distance where fog is maximum
     */
    public void setFogRange(float start, float end) {
        this.fogStart = start;
        this.fogEnd = end;
    }

    /**
     * Sets the height scale for parallax occlusion mapping.
     * Higher values = more pronounced depth effect.
     *
     * @param scale Height scale (recommended: 0.05 - 0.15)
     */
    public void setHeightScale(float scale) {
        this.heightScale = scale;
    }

    /**
     * Gets the current height scale for parallax occlusion mapping.
     *
     * @return Height scale value
     */
    public float getHeightScale() {
        return heightScale;
    }

    /**
     * Sets the point lights for ceiling lamps.
     *
     * @param positions Array of light positions
     * @param colors Array of light colors
     * @param intensities Array of light intensities
     * @param count Number of lights to use
     */
    public void setPointLights(final Vector3[] positions, final Vector3[] colors,
                                final float[] intensities, final int count) {
        this.numPointLights = Math.min(count, 20); // Max 20 lights
        for (int i = 0; i < numPointLights; i++) {
            this.pointLightPositions[i].set(positions[i]);
            this.pointLightColors[i].set(colors[i]);
            this.pointLightIntensities[i] = intensities[i];
        }
    }
}
