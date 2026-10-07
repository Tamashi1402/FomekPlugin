package __RENDERAPI_PACKAGE__;

import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * FomekShader — a full shader configuration object.
 *
 * Level 1 — Material settings (render type, color, transparency, glowing, texture)
 * Level 2 — Uniform variables (named float/vec3 values sent to the shader program)
 * Level 3 — Custom GLSL source (vertex + fragment shader code)
 * Level 4 — Shader program binding (named registered shader programs)
 *
 * Uniforms are stored by name and applied when the shader is bound.
 * Custom GLSL source is stored but requires a registered shader program to compile.
 */
public class FomekShader {

    // ── Level 1: Material settings ──────────────────────────────────────────
    private String renderType = "entityCutoutNoCull";
    private int color = -1;              // -1 = no tint
    private float transparency = 1.0f;
    private boolean glowing = false;
    private float glowStrength = 1.0f;   // 0..1 emission intensity
    private String texture = null;       // null = use model's texture
    private String drawOrder = "default";  // "default" or "behind"

    // ── energySwirl controls ────────────────────────────────────────────────
    private float swirlXSpeed = 1.0f;  // UV x scroll speed multiplier (positive = right, negative = left)
    private float swirlZSpeed = 1.0f;  // UV z(y) scroll speed multiplier (positive = down, negative = up)
    private String swirlBlendMode = null;  // null = use global currentBlendMode, otherwise override (ADDITION, ALPHA, SCREEN, etc.)

    // ── Level 2: Uniform variables ──────────────────────────────────────────
    private final Map<String, Float> floatUniforms = new HashMap<>();
    private final Map<String, float[]> vec3Uniforms = new HashMap<>();
    private final Map<String, float[]> vec2Uniforms = new HashMap<>();
    private final Map<String, float[]> vec4Uniforms = new HashMap<>();
    private final Map<String, Integer> intUniforms = new HashMap<>();
    private final Map<String, Boolean> boolUniforms = new HashMap<>();

    // ── Level 3: Custom GLSL source ─────────────────────────────────────────
    private String vertexShaderSource = null;
    private String fragmentShaderSource = null;
    private String shaderProgramName = null;  // name of registered ShaderInstance

    // ── Saved global state (for apply/restore) ──────────────────────────────
    private float[] savedShaderColor = new float[]{1f, 1f, 1f, 1f};

    public FomekShader() {}

    // ── Level 1 setters ─────────────────────────────────────────────────────
    public void setRenderType(String rt) { this.renderType = rt; }
    public void setColor(int c)          { this.color = c; }
    public void setTransparency(float t) { this.transparency = Math.max(0f, Math.min(1f, t)); }
    public void setGlowing(boolean g)    { this.glowing = g; }
    public void setGlowStrength(float s) { this.glowStrength = Math.max(0f, Math.min(1f, s)); }
    public void setTexture(String t)     { this.texture = t; }
    public void setDrawOrder(String d)    { this.drawOrder = d; }
    public void setSwirlXSpeed(float s)  { this.swirlXSpeed = s; }
    public void setSwirlZSpeed(float s)  { this.swirlZSpeed = s; }
    public void setSwirlBlendMode(String m) { this.swirlBlendMode = m; }

    // ── Level 1 getters ─────────────────────────────────────────────────────
    public String getRenderType()   { return renderType; }
    public int getColor()           { return color; }
    public float getTransparency()  { return transparency; }
    public boolean isGlowing()      { return glowing; }
    public float getGlowStrength()  { return glowStrength; }
    public String getTexture()      { return texture; }
    public String getDrawOrder()    { return drawOrder; }
    public float getSwirlXSpeed()   { return swirlXSpeed; }
    public float getSwirlZSpeed()   { return swirlZSpeed; }
    public String getSwirlBlendMode() { return swirlBlendMode; }

    // ── Level 2: Uniform setters ─────────────────────────────────────────────
    public void setFloat(String name, float value)  { floatUniforms.put(name, value); }
    public void setVec3(String name, float x, float y, float z) { vec3Uniforms.put(name, new float[]{x, y, z}); }
    public void setInt(String name, int value)        { intUniforms.put(name, value); }
    public void setBool(String name, boolean value)   { boolUniforms.put(name, value); }
    public void setVec2(String name, float x, float y) { vec2Uniforms.put(name, new float[]{x, y}); }
    public void setVec4(String name, float x, float y, float z, float w) { vec4Uniforms.put(name, new float[]{x, y, z, w}); }

    // ── Level 2: Uniform getters ─────────────────────────────────────────────
    public float getFloat(String name) { return floatUniforms.getOrDefault(name, 0f); }
    public float[] getVec3(String name) { return vec3Uniforms.getOrDefault(name, new float[]{0,0,0}); }
    public int getInt(String name)       { return intUniforms.getOrDefault(name, 0); }
    public boolean getBool(String name)  { return boolUniforms.getOrDefault(name, false); }
    public float[] getVec2(String name)  { return vec2Uniforms.getOrDefault(name, new float[]{0,0}); }
    public float[] getVec4(String name)  { return vec4Uniforms.getOrDefault(name, new float[]{0,0,0,0}); }
    public boolean hasFloat(String name) { return floatUniforms.containsKey(name); }

    // ── Level 2: Get all uniforms (for apply) ───────────────────────────────
    public Map<String, Float>    getFloatUniforms() { return floatUniforms; }
    public Map<String, float[]> getVec3Uniforms() { return vec3Uniforms; }
    public Map<String, float[]> getVec2Uniforms() { return vec2Uniforms; }
    public Map<String, float[]> getVec4Uniforms() { return vec4Uniforms; }
    public Map<String, Integer>  getIntUniforms()  { return intUniforms; }
    public Map<String, Boolean>  getBoolUniforms() { return boolUniforms; }

    // ── Level 3: Custom GLSL ─────────────────────────────────────────────────
    public void setVertexShaderSource(String src)   { this.vertexShaderSource = src; }
    public void setFragmentShaderSource(String src) { this.fragmentShaderSource = src; }
    public void setShaderProgramName(String name)   { this.shaderProgramName = name; }

    public String getVertexShaderSource()   { return vertexShaderSource; }
    public String getFragmentShaderSource()  { return fragmentShaderSource; }
    public String getShaderProgramName()     { return shaderProgramName; }

    // ── Apply / Restore RenderSystem state ───────────────────────────────────

    public void apply() {
        // CRITICAL: Save the current global shader color BEFORE rendering.
        //
        // RenderSystem.setShaderColor() is a GLOBAL state that is NOT managed
        // by RenderType's setupRenderState() / clearRenderState(). It persists
        // across ALL batch flushes and acts as a color multiplier on every
        // vertex drawn.
        //
        // The entity renderer (LivingEntityRenderer) may set setShaderColor()
        // for effects like the hurt flash. If we blindly reset it to (1,1,1,1)
        // in restore(), we overwrite the entity renderer's value, and when
        // the player's batch is flushed at end of frame, it uses OUR value
        // instead of the entity renderer's value — causing visual contamination.
        //
        // All visual effects (color tint, transparency, glow boost) are baked
        // per-vertex by applyColor() inside renderWithShader(). We do NOT use
        // RenderSystem.setShaderColor() for the custom model's effects.
        savedShaderColor = RenderSystem.getShaderColor();
    }

    public void applyUniforms() {
        // Apply uniforms to the currently bound shader program.
        // In 1.21.1, ShaderInstance.getUniform(name) returns a Uniform object (or null).
        // Call uniform.set(...) on it — NOT shader.setUniform(name, value).
        try {
            net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) return;

            for (Map.Entry<String, Float> entry : floatUniforms.entrySet()) {
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) {
                    u.set(entry.getValue());
                }
            }

            for (Map.Entry<String, float[]> entry : vec3Uniforms.entrySet()) {
                float[] v = entry.getValue();
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) {
                    u.set(v[0], v[1], v[2]);
                }
            }

            for (Map.Entry<String, float[]> entry : vec2Uniforms.entrySet()) {
                float[] v = entry.getValue();
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) { u.set(v[0], v[1]); }
            }

            for (Map.Entry<String, float[]> entry : vec4Uniforms.entrySet()) {
                float[] v = entry.getValue();
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) { u.set(v[0], v[1], v[2], v[3]); }
            }

            for (Map.Entry<String, Integer> entry : intUniforms.entrySet()) {
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) {
                    u.set(entry.getValue());
                }
            }

            for (Map.Entry<String, Boolean> entry : boolUniforms.entrySet()) {
                com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(entry.getKey());
                if (u != null) { u.set(entry.getValue() ? 1 : 0); }
            }
        } catch (Exception e) {
            // Uniforms may not exist on this shader — silently ignore
        }
    }

    public void restore() {
        // Restore the saved shader color — NOT a blind (1,1,1,1) reset.
        // This ensures the entity renderer's setShaderColor() (e.g., hurt
        // flash, freeze effect) is preserved for the player's batch at
        // flush time.
        RenderSystem.setShaderColor(
            savedShaderColor[0], savedShaderColor[1],
            savedShaderColor[2], savedShaderColor[3]);
    }

    // ── Light override ──────────────────────────────────────────────────────

    public int getPackedLight(int defaultLight) {
        return glowing ? LightTexture.FULL_BRIGHT : defaultLight;
    }

    // ── Color override ──────────────────────────────────────────────────────

    public int applyColor(int vertexColor) {
        if (color == -1 && transparency >= 1.0f) return vertexColor;

        int r, g, b, a;

        if (color != -1) {
            r = (color >> 16) & 0xFF;
            g = (color >> 8)  & 0xFF;
            b =  color        & 0xFF;
            a = (color >> 24) & 0xFF;
            if (a == 0) a = 255;
        } else {
            r = (vertexColor >> 16) & 0xFF;
            g = (vertexColor >> 8)  & 0xFF;
            b =  vertexColor        & 0xFF;
            a = (vertexColor >> 24) & 0xFF;
            if (a == 0) a = 255;
        }

        if (transparency < 1.0f) {
            a = (int)(a * transparency);
        }

        // Glow strength boosts color brightness
        if (glowing && glowStrength > 0) {
            float boost = 1.0f + glowStrength * 0.5f;
            r = Math.min(255, (int)(r * boost));
            g = Math.min(255, (int)(g * boost));
            b = Math.min(255, (int)(b * boost));
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shader Manager (inner class — merged from FomekShaderManager.java)
    // ═══════════════════════════════════════════════════════════════════════════

    public static class Manager {

        // ── Program cache ────────────────────────────────────────────────────────

            /** key = vertHash_fragHash → GL program id */
            private static final Map<String, Integer>               programCache    = new HashMap<>();
            /** programId → { uniformName → location } */
            private static final Map<Integer, Map<String, Integer>> uniformLocCache = new HashMap<>();
            /** programId → { attribName → location } */
            private static final Map<Integer, Map<String, Integer>> attribLocCache  = new HashMap<>();

            // GL type constants used during uniform type detection
            private static final int GL_FLOAT      = 0x1406;
            private static final int GL_FLOAT_VEC2 = 0x8B50;
            private static final int GL_FLOAT_VEC3 = 0x8B51;
            private static final int GL_FLOAT_VEC4 = 0x8B52;
            private static final int GL_INT        = 0x1404;

            // ── Public API ───────────────────────────────────────────────────────────

            /**
             * Get or compile a GL program from GLSL source strings.
             * Returns 0 on failure.
             * Safe to call every frame — result is cached by source hash.
             */
            public static int getOrCreateProgram(String vertexSrc, String fragmentSrc) {
                if (vertexSrc == null || fragmentSrc == null) return 0;
                if (vertexSrc.trim().isEmpty() || fragmentSrc.trim().isEmpty()) return 0;

                String key = vertexSrc.hashCode() + "_" + fragmentSrc.hashCode();
                Integer cached = programCache.get(key);
                if (cached != null) return cached;

                int pid = compileProgram(vertexSrc, fragmentSrc);
                if (pid != 0) {
                    programCache.put(key, pid);
                    uniformLocCache.put(pid, queryUniforms(pid));
                    attribLocCache.put(pid, queryAttributes(pid));
                }
                return pid;
            }

            /**
             * Upload ModelViewMat and ProjMat to the given GL program.
             * Must be called after glUseProgram(programId).
             */
            public static void applyMatrices(int programId, Matrix4f modelView, Matrix4f projection) {
                if (programId == 0) return;
                Map<String, Integer> locs = uniformLocCache.get(programId);
                if (locs == null) return;

                Integer mvLoc  = locs.get("ModelViewMat");
                Integer projLoc = locs.get("ProjMat");

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    if (mvLoc != null && mvLoc >= 0) {
                        FloatBuffer buf = stack.mallocFloat(16);
                        modelView.get(buf);
                        GL20.glUniformMatrix4fv(mvLoc, false, buf);
                    }
                    if (projLoc != null && projLoc >= 0) {
                        FloatBuffer buf = stack.mallocFloat(16);
                        projection.get(buf);
                        GL20.glUniformMatrix4fv(projLoc, false, buf);
                    }
                }
            }

            /**
             * Get the uniform location map for a program (or null if not cached).
             */
            public static Map<String, Integer> getUniformLocations(int programId) {
                return uniformLocCache.get(programId);
            }

            /**
             * Get the attribute location for a named attribute in a program.
             * Returns -1 if not found.
             */
            public static int getAttribLocation(int programId, String name) {
                Map<String, Integer> attribs = attribLocCache.get(programId);
                if (attribs == null) return -1;
                Integer loc = attribs.get(name);
                return loc != null ? loc : -1;
            }

            /**
             * Apply FomekShader uniforms to the currently bound GL program.
             * Must be called after glUseProgram(programId).
             */
            public static void applyUniforms(int programId, FomekShader fomekShader) {
                if (programId == 0 || fomekShader == null) return;
                Map<String, Integer> locs = uniformLocCache.get(programId);
                if (locs == null) return;

                for (Map.Entry<String, Float> e : fomekShader.getFloatUniforms().entrySet()) {
                    Integer loc = locs.get(e.getKey());
                    if (loc != null && loc >= 0) GL20.glUniform1f(loc, e.getValue());
                }
                for (Map.Entry<String, float[]> e : fomekShader.getVec3Uniforms().entrySet()) {
                    Integer loc = locs.get(e.getKey());
                    float[] v = e.getValue();
                    if (loc != null && loc >= 0) GL20.glUniform3f(loc, v[0], v[1], v[2]);
                }
                for (Map.Entry<String, float[]> e : fomekShader.getVec2Uniforms().entrySet()) {
                    Integer loc = locs.get(e.getKey());
                    float[] v = e.getValue();
                    if (loc != null && loc >= 0) GL20.glUniform2f(loc, v[0], v[1]);
                }
                for (Map.Entry<String, float[]> e : fomekShader.getVec4Uniforms().entrySet()) {
                    Integer loc = locs.get(e.getKey());
                    float[] v = e.getValue();
                    if (loc != null && loc >= 0) GL20.glUniform4f(loc, v[0], v[1], v[2], v[3]);
                }
                for (Map.Entry<String, Integer> e : fomekShader.getIntUniforms().entrySet()) {
                    Integer loc = locs.get(e.getKey());
                    if (loc != null && loc >= 0) GL20.glUniform1i(loc, e.getValue());
                }
            }

            // ── Shader compilation ───────────────────────────────────────────────────

            private static int compileProgram(String vertSrc, String fragSrc) {
                int vsh = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
                GL20.glShaderSource(vsh, vertSrc);
                GL20.glCompileShader(vsh);
                if (GL20.glGetShaderi(vsh, GL20.GL_COMPILE_STATUS) == 0) {
                    System.err.println("[FomekCore] Vertex shader compile error:\n"
                        + GL20.glGetShaderInfoLog(vsh, 4096));
                    GL20.glDeleteShader(vsh);
                    return 0;
                }

                int fsh = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
                GL20.glShaderSource(fsh, fragSrc);
                GL20.glCompileShader(fsh);
                if (GL20.glGetShaderi(fsh, GL20.GL_COMPILE_STATUS) == 0) {
                    System.err.println("[FomekCore] Fragment shader compile error:\n"
                        + GL20.glGetShaderInfoLog(fsh, 4096));
                    GL20.glDeleteShader(vsh);
                    GL20.glDeleteShader(fsh);
                    return 0;
                }

                int pid = GL20.glCreateProgram();
                GL20.glAttachShader(pid, vsh);
                GL20.glAttachShader(pid, fsh);
                GL20.glLinkProgram(pid);
                GL20.glDetachShader(pid, vsh);
                GL20.glDetachShader(pid, fsh);
                GL20.glDeleteShader(vsh);
                GL20.glDeleteShader(fsh);

                if (GL20.glGetProgrami(pid, GL20.GL_LINK_STATUS) == 0) {
                    System.err.println("[FomekCore] Shader link error:\n"
                        + GL20.glGetProgramInfoLog(pid, 4096));
                    GL20.glDeleteProgram(pid);
                    return 0;
                }

                System.out.println("[FomekCore] Custom shader compiled OK (programId=" + pid + ")");
                return pid;
            }

            private static Map<String, Integer> queryUniforms(int pid) {
                Map<String, Integer> locs = new HashMap<>();
                int count = GL20.glGetProgrami(pid, GL20.GL_ACTIVE_UNIFORMS);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer sizeBuf = stack.mallocInt(1);
                    IntBuffer typeBuf = stack.mallocInt(1);
                    for (int i = 0; i < count; i++) {
                        String name = GL20.glGetActiveUniform(pid, i, 256, sizeBuf, typeBuf);
                        if (name == null || name.isEmpty()) continue;
                        int loc = GL20.glGetUniformLocation(pid, name);
                        if (loc >= 0) {
                            locs.put(name, loc);
                            System.out.println("[FomekCore]   uniform '" + name + "' @ loc=" + loc);
                        }
                    }
                }
                return locs;
            }

            private static Map<String, Integer> queryAttributes(int pid) {
                Map<String, Integer> locs = new HashMap<>();
                int count = GL20.glGetProgrami(pid, GL20.GL_ACTIVE_ATTRIBUTES);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer sizeBuf = stack.mallocInt(1);
                    IntBuffer typeBuf = stack.mallocInt(1);
                    for (int i = 0; i < count; i++) {
                        String name = GL20.glGetActiveAttrib(pid, i, 256, sizeBuf, typeBuf);
                        if (name == null || name.isEmpty()) continue;
                        int loc = GL20.glGetAttribLocation(pid, name);
                        if (loc >= 0) {
                            locs.put(name, loc);
                            System.out.println("[FomekCore]   attrib '" + name + "' @ loc=" + loc);
                        }
                    }
                }
                return locs;
            }

            // ── Reflection helpers ───────────────────────────────────────────────────

            private static Object getShard(String fieldName) {
                for (Class<?> c : new Class<?>[]{RenderStateShard.class, RenderType.class}) {
                    try {
                        Field f = c.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        return f.get(null);
                    } catch (Exception ignored) {}
                }
                return null;
            }

            private static void setBuilderShard(RenderType.CompositeState.CompositeStateBuilder builder,
                                                 String shardName, String method, Class<?> shardClass) {
                Object shard = getShard(shardName);
                if (shard == null) return;
                try {
                    RenderType.CompositeState.CompositeStateBuilder.class
                        .getMethod(method, shardClass)
                        .invoke(builder, shard);
                } catch (Exception ignored) {}
            }

    }


    // ── NBT Serialization (for BEWRL storage sync) ─────────────────────────────

    public net.minecraft.nbt.CompoundTag toNBT() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (renderType != null) tag.putString("renderType", renderType);
        tag.putInt("color", color);
        tag.putFloat("transparency", transparency);
        tag.putBoolean("glowing", glowing);
        tag.putFloat("glowStrength", glowStrength);
        if (texture != null) tag.putString("texture", texture);
        if (drawOrder != null) tag.putString("drawOrder", drawOrder);
        tag.putFloat("swirlXSpeed", swirlXSpeed);
        tag.putFloat("swirlZSpeed", swirlZSpeed);
        if (swirlBlendMode != null) tag.putString("swirlBlendMode", swirlBlendMode);
        if (vertexShaderSource != null) tag.putString("vertexSrc", vertexShaderSource);
        if (fragmentShaderSource != null) tag.putString("fragmentSrc", fragmentShaderSource);
        if (shaderProgramName != null) tag.putString("programName", shaderProgramName);

        // Uniforms
        net.minecraft.nbt.CompoundTag floats = new net.minecraft.nbt.CompoundTag();
        for (var e : floatUniforms.entrySet()) floats.putFloat(e.getKey(), e.getValue());
        tag.put("floats", floats);

        net.minecraft.nbt.ListTag vec3List = new net.minecraft.nbt.ListTag();
        for (var e : vec3Uniforms.entrySet()) {
            net.minecraft.nbt.CompoundTag v = new net.minecraft.nbt.CompoundTag();
            v.putString("name", e.getKey());
            float[] arr = e.getValue();
            v.putFloat("x", arr[0]); v.putFloat("y", arr[1]); v.putFloat("z", arr[2]);
            vec3List.add(v);
        }
        tag.put("vec3s", vec3List);

        return tag;
    }

    public static FomekShader fromNBT(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        FomekShader s = new FomekShader();
        if (tag.contains("renderType")) s.renderType = tag.getString("renderType");
        s.color = tag.getInt("color");
        s.transparency = tag.getFloat("transparency");
        s.glowing = tag.getBoolean("glowing");
        s.glowStrength = tag.getFloat("glowStrength");
        if (tag.contains("texture")) s.texture = tag.getString("texture");
        if (tag.contains("drawOrder")) s.drawOrder = tag.getString("drawOrder");
        if (tag.contains("swirlXSpeed")) s.swirlXSpeed = tag.getFloat("swirlXSpeed");
        if (tag.contains("swirlZSpeed")) s.swirlZSpeed = tag.getFloat("swirlZSpeed");
        if (tag.contains("swirlBlendMode")) s.swirlBlendMode = tag.getString("swirlBlendMode");
        if (tag.contains("vertexSrc")) s.vertexShaderSource = tag.getString("vertexSrc");
        if (tag.contains("fragmentSrc")) s.fragmentShaderSource = tag.getString("fragmentSrc");
        if (tag.contains("programName")) s.shaderProgramName = tag.getString("programName");

        if (tag.contains("floats")) {
            net.minecraft.nbt.CompoundTag floats = tag.getCompound("floats");
            for (String name : floats.getAllKeys()) s.floatUniforms.put(name, floats.getFloat(name));
        }
        if (tag.contains("vec3s")) {
            net.minecraft.nbt.ListTag vec3List = tag.getList("vec3s", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < vec3List.size(); i++) {
                net.minecraft.nbt.CompoundTag v = vec3List.getCompound(i);
                s.vec3Uniforms.put(v.getString("name"),
                        new float[]{v.getFloat("x"), v.getFloat("y"), v.getFloat("z")});
            }
        }
        return s;
    }

}
