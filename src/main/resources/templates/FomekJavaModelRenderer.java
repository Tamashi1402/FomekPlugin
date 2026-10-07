package __RENDERAPI_PACKAGE__;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders named Java models (baked ModelPart layers) on the Fomek render pipeline.
 *
 * In MC 1.21.1, EntityModelSet.bakeLayer() returns a ModelPart (the root part),
 * NOT an EntityModel<?>. ModelPart has its own render() method, so we store and
 * render ModelPart directly — no EntityModel wrapper needed.
 *
 * Three ways to register a model:
 *
 * 1. ModelLayerLocation (auto-registered by entity renderers):
 *      FomekJavaModelRenderer.registerModelLayer("Helmet",
 *          new ModelLayerLocation(RL.fromNamespaceAndPath("mymod", "helmet"), "main"));
 *
 * 2. Direct class name (for MCreator Java models NOT attached to an entity):
 *      FomekJavaModelRenderer.registerModelClass("Helmet",
 *          "net.mcreator.mymod.client.model.ModelHelmet");
 *    Or just pass the class name directly to renderJavaModel/getBakedModel.
 *
 * 3. Pre-baked ModelPart (full manual control):
 *      ModelPart part = MyModel.createBodyLayer().bakeRoot();
 *      FomekJavaModelRenderer.registerBakedModel("Helmet", part);
 *
 * The reflection fallback also tries common MCreator package patterns.
 */
@OnlyIn(Dist.CLIENT)
public class FomekJavaModelRenderer {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, ModelLayerLocation> MODEL_LAYERS = new HashMap<>();
    private static final Map<String, String> MODEL_CLASSES = new HashMap<>();
    private static final Map<String, ModelPart> BAKED_MODELS = new HashMap<>();
    private static final Map<String, ModelPart> MODEL_CACHE = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration API
    // ═══════════════════════════════════════════════════════════════════════════

    public static void registerModelLayer(String name, ModelLayerLocation location) {
        MODEL_LAYERS.put(name.toLowerCase(), location);
        MODEL_CACHE.remove(name.toLowerCase());
    }

    public static void registerModelClass(String name, String className) {
        MODEL_CLASSES.put(name.toLowerCase(), className);
        MODEL_CACHE.remove(name.toLowerCase());
    }

    public static void registerBakedModel(String name, ModelPart model) {
        if (model != null) {
            BAKED_MODELS.put(name.toLowerCase(), model);
            MODEL_CACHE.put(name.toLowerCase(), model);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model lookup
    // ═══════════════════════════════════════════════════════════════════════════

    private static ModelPart getModel(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = name.toLowerCase();

        // 0. If the name looks like a class path, bake directly via reflection
        if (name.contains(".") && !name.contains(":")) {
            ModelPart model = bakeByClassName(name);
            if (model != null) {
                MODEL_CACHE.put(key, model);
                return model;
            }
        }

        ModelPart cached = MODEL_CACHE.get(key);
        if (cached != null) return cached;

        // 1. Pre-baked models
        ModelPart preBaked = BAKED_MODELS.get(key);
        if (preBaked != null) {
            MODEL_CACHE.put(key, preBaked);
            return preBaked;
        }

        // 2. Registered class name
        String className = MODEL_CLASSES.get(key);
        if (className != null) {
            ModelPart model = bakeByClassName(className);
            if (model != null) {
                MODEL_CACHE.put(key, model);
                return model;
            }
        }

        // 3. Explicit ModelLayerLocation
        ModelLayerLocation layer = MODEL_LAYERS.get(key);
        if (layer != null) {
            ModelPart model = bakeLayerSafe(layer);
            if (model != null && !isEmptyModel(model)) {
                MODEL_CACHE.put(key, model);
                return model;
            }
        }

        // 4. Guess ModelLayerLocation
        ResourceLocation[] guesses = {
            ResourceLocation.fromNamespaceAndPath("minecraft", key),
            ResourceLocation.fromNamespaceAndPath("__MODID__", key),
            ResourceLocation.fromNamespaceAndPath("__MODID__", "model_" + key),
            ResourceLocation.fromNamespaceAndPath("minecraft", "model_" + key),
        };
        for (ResourceLocation guess : guesses) {
            ModelLayerLocation guessLayer = new ModelLayerLocation(guess, "main");
            ModelPart model = bakeLayerSafe(guessLayer);
            if (model != null && !isEmptyModel(model)) {
                MODEL_CACHE.put(key, model);
                return model;
            }
        }

        // 5. Reflection fallback
        String simpleName = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        String capName = Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1);
        List<String> searchPaths = buildReflectionSearchPaths(capName);
        for (String classPath : searchPaths) {
            ModelPart model = bakeByClassName(classPath);
            if (model != null) {
                LOGGER.info("Fomek: Java model '{}' found via reflection at {}", name, classPath);
                MODEL_CACHE.put(key, model);
                return model;
            }
        }

        LOGGER.warn("Fomek: Java model '{}' not found in any registry. " +
                "Use registerModelLayer(), registerModelClass(), or registerBakedModel() " +
                "to make it available.", name);
        return null;
    }

    private static List<String> buildReflectionSearchPaths(String capName) {
        List<String> paths = new ArrayList<>();
        paths.add("__MOD_PACKAGE__.client.model.Model" + capName);
        paths.add("__MOD_PACKAGE__.client.model.model" + capName);
        paths.add("__MOD_PACKAGE__.model.Model" + capName);
        paths.add("net.mcreator." + capName.toLowerCase() + ".client.model.Model" + capName);
        paths.add("model.Model" + capName);
        paths.add("Model" + capName);
        return paths;
    }

    private static ModelPart bakeByClassName(String className) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = FomekJavaModelRenderer.class.getClassLoader();
            Class<?> modelClass = Class.forName(className, false, cl);

            java.lang.reflect.Method m = modelClass.getMethod("createBodyLayer");
            Object layerDef = m.invoke(null);

            java.lang.reflect.Method bakeRoot = layerDef.getClass().getMethod("bakeRoot");
            ModelPart model = (ModelPart) bakeRoot.invoke(layerDef);

            if (model != null && !isEmptyModel(model)) {
                return model;
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.debug("Fomek: Failed to bake model class {}: {}", className, e.getMessage());
        }
        return null;
    }

    private static ModelPart bakeLayerSafe(ModelLayerLocation layer) {
        try {
            return Minecraft.getInstance().getEntityModels().bakeLayer(layer);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Check if a ModelPart is effectively empty (no cubes and no children).
     * EntityModelSet.bakeLayer() returns an empty ModelPart for unregistered layers.
     * In NeoForge 1.21.1, cubes and children are private fields, so we use reflection.
     */
    private static boolean isEmptyModel(ModelPart model) {
        if (model == null) return true;
        try {
            java.lang.reflect.Field cubesF = ModelPart.class.getDeclaredField("cubes");
            java.lang.reflect.Field childrenF = ModelPart.class.getDeclaredField("children");
            cubesF.setAccessible(true);
            childrenF.setAccessible(true);
            List<?> cubes = (List<?>) cubesF.get(model);
            Map<?, ?> children = (Map<?, ?>) childrenF.get(model);
            return cubes != null && cubes.isEmpty() && children != null && children.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Public accessor: get a baked ModelPart by name.
     */
    public static ModelPart getBakedModel(String name) {
        return getModel(name);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bounding box calculation (for centering)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates the bounding box center of a ModelPart hierarchy in PIXEL space.
     * Traverses all children recursively, accumulating position offsets.
     * Ignores bone rotations (standard Blockbench exports have 0 rotation on root).
     * Returns [cx, cy, cz] in pixels.
     */
    public static float[] calculateModelCenter(ModelPart root) {
        float[] bbox = calculateBoundingBox(root, 0f, 0f, 0f);
        // bbox = [minX, minY, minZ, maxX, maxY, maxZ] in pixel space
        float cx = (bbox[0] + bbox[3]) / 2f;
        float cy = (bbox[1] + bbox[4]) / 2f;
        float cz = (bbox[2] + bbox[5]) / 2f;
        return new float[] { cx, cy, cz };
    }

    /**
     * Recursively computes the bounding box of a ModelPart and all its children.
     * parentX/Y/Z is the accumulated position offset from ancestor parts.
     * Returns [minX, minY, minZ, maxX, maxY, maxZ] in pixel space.
     */
    private static float[] calculateBoundingBox(ModelPart part, float parentX, float parentY, float parentZ) {
        float accX = parentX + part.x;
        float accY = parentY + part.y;
        float accZ = parentZ + part.z;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        try {
            java.lang.reflect.Field cubesF = ModelPart.class.getDeclaredField("cubes");
            cubesF.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ModelPart.Cube> cubes = (List<ModelPart.Cube>) cubesF.get(part);

            if (cubes != null) {
                for (ModelPart.Cube cube : cubes) {
                    // Cube has public fields minX/Y/Z, maxX/Y/Z (in pixel space, relative to part pivot)
                    minX = Math.min(minX, accX + cube.minX);
                    minY = Math.min(minY, accY + cube.minY);
                    minZ = Math.min(minZ, accZ + cube.minZ);
                    maxX = Math.max(maxX, accX + cube.maxX);
                    maxY = Math.max(maxY, accY + cube.maxY);
                    maxZ = Math.max(maxZ, accZ + cube.maxZ);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Fomek: Could not access cubes for bounding box: {}", e.getMessage());
        }

        // Process children
        try {
            java.lang.reflect.Field childrenF = ModelPart.class.getDeclaredField("children");
            childrenF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ModelPart> children = (Map<String, ModelPart>) childrenF.get(part);

            if (children != null) {
                for (ModelPart child : children.values()) {
                    float[] childBbox = calculateBoundingBox(child, accX, accY, accZ);
                    if (childBbox[0] < Float.MAX_VALUE) {
                        minX = Math.min(minX, childBbox[0]);
                        minY = Math.min(minY, childBbox[1]);
                        minZ = Math.min(minZ, childBbox[2]);
                        maxX = Math.max(maxX, childBbox[3]);
                        maxY = Math.max(maxY, childBbox[4]);
                        maxZ = Math.max(maxZ, childBbox[5]);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Fomek: Could not access children for bounding box: {}", e.getMessage());
        }

        // If no geometry found, fall back to the part's own position
        if (minX == Float.MAX_VALUE) {
            return new float[] { accX, accY, accZ, accX, accY, accZ };
        }

        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Legacy overload — defaults to non-GUI context (third person / world).
     */
    public static void renderJavaModel(
            String name, ResourceLocation texture, String renderType,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale,
            PoseStack pose, MultiBufferSource buffer,
            int packedLight, int packedOverlay) {
        renderJavaModel(name, texture, renderType, x, y, z, yaw, pitch, roll, scale,
                pose, buffer, packedLight, packedOverlay, false, false);
    }

    /**
     * Renders a Java entity model (ModelPart) into the current item/overlay render context.
     *
     * COORDINATE SYSTEM EXPLANATION:
     *
     * Entity models (ModelPart) from Blockbench 1.17+ exports:
     *   - Coordinates in PIXEL space (16 pixels = 1 block)
     *   - Y points UP (head at positive Y, feet at Y=0)
     *   - Face/front faces +Z (entity forward = +Z at yaw=0)
     *   - Root ModelPart is at the entity's feet (0,0,0)
     *   - Body parts are children with PartPose offsets (e.g., head at y=24/26)
     *
     * ModelPart.render() internally divides ALL coordinates by 16 (pixel → block).
     * So the output of model.render() is in BLOCK SPACE (1 unit = 1 block).
     *
     * Item rendering context (from the mixin at HEAD of renderStatic):
     *   - THIRD_PERSON / FIRST_PERSON / GROUND: PoseStack is in BLOCK SPACE (Y up)
     *     → ModelPart's /16 gives correct block-space size. No scale needed.
     *   - GUI: PoseStack is in SCREEN PIXEL SPACE (Y goes DOWN, no Y flip applied yet
     *     because the display transform that would handle it is cancelled)
     *     → Need scale(16) to convert block→pixel (match BakedModel's 0-16 range)
     *     → Need Z 180° to flip Y from up→down (screen Y is down, model Y is up)
     *
     * The transform chain (PoseStack order — outermost first, innermost applied
     * to vertices first):
     *
     *   [User transforms]     ← user's x/y/z/yaw/pitch/roll/scale
     *   [scale(16)]          ← GUI only: block→pixel space
     *   [Y 180°]             ← flip facing: entity +Z → item -Z (toward camera)
     *   [Z 180°]             ← GUI only: flip Y to match screen coordinates
     *   [translate(-center)] ← center model at origin (innermost, rotates around center)
     *   model.render()       ← ModelPart handles /16 internally
     *
     * @param isGuiContext true when rendering in GUI/inventory (screen pixel space,
     *                     Y goes down), false for world/hand contexts (block space, Y up)
     */
    public static void renderJavaModel(
            String name, ResourceLocation texture, String renderType,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale,
            PoseStack pose, MultiBufferSource buffer,
            int packedLight, int packedOverlay,
            boolean isGuiContext) {
        renderJavaModel(name, texture, renderType, x, y, z, yaw, pitch, roll, scale,
                pose, buffer, packedLight, packedOverlay, isGuiContext, false);
    }

    public static void renderJavaModel(
            String name, ResourceLocation texture, String renderType,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale,
            PoseStack pose, MultiBufferSource buffer,
            int packedLight, int packedOverlay,
            boolean isGuiContext, boolean isWorldContext) {

        ModelPart model = getModel(name);
        if (model == null) return;

        RenderType rt = resolveRenderType(renderType, texture);
        VertexConsumer consumer = buffer.getBuffer(rt);

        // ── Calculate model center (in pixel space, then convert to block space) ──
        float[] center = calculateModelCenter(model);
        float cx = center[0] / 16f;  // block space
        float cy = center[1] / 16f;
        float cz = center[2] / 16f;

        pose.pushPose();

        // 1. User transforms (in the PoseStack's current space — block for world, pixel for GUI)
        pose.translate(x, y, z);
        if (yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        if (scale != 1) pose.scale(scale, scale, scale);

        // 2. World context: Minecraft's ModelPart uses a Y-DOWN coordinate convention.
        //    Vanilla LivingEntityRenderer applies scale(-1, -1, 1) to flip Y to world-up.
        //    Without this, models appear upside down in world rendering.
        //    Applied AFTER user transforms so the user's x/y/z/rotations work in world space.
        //    NOT applied for item/overlay contexts — those have their own coordinate handling.
        if (isWorldContext) {
            pose.scale(-1.0F, -1.0F, 1.0F);
        }

        // 3. No extra scale needed for any context.
        //    ModelPart.render() handles /16 internally. The GUI PoseStack at mixin HEAD
        //    is in the same normalized block space as world contexts — no scale(16) needed.

        // 4. Y 180°: Entity models face +Z (forward at yaw=0).
        //    Items in GUI/third-person expect -Z (toward camera).
        //    This flips the facing direction. Needed for ALL contexts.
        pose.mulPose(Axis.YP.rotationDegrees(180));

        // 5. Z 180°: GUI only. Screen Y goes DOWN, but entity model Y goes UP.
        //    Without this, the model is upside down in the GUI.
        //    Combined with Y 180°: X stays, Y flips (fixes orientation), Z flips (fixes facing).
        //    For world contexts, Y is already up, so this would make the model upside down.
        if (isGuiContext) {
            pose.mulPose(Axis.ZP.rotationDegrees(180));
        }

        // 6. Center the model: translate by negative center (in block space).
        //    This is the INNERMOST transform — applied to vertices first, before rotations.
        //    This makes the model rotate around its center, not its feet.
        pose.translate(-cx, -cy, -cz);

        // 7. Render — ModelPart.render() internally divides by 16 (pixel → block)
        model.render(pose, consumer, packedLight, packedOverlay);

        pose.popPose();
    }

    public static RenderType resolveRenderType(String renderType, ResourceLocation texture) {
        if (texture == null) return RenderType.entityCutout(
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png"));
        if (renderType == null || renderType.isEmpty()) renderType = "entityCutoutNoCull";

        // If a custom blend mode is active, use entityTranslucent (which enables blend)
        // so the manual flush in flushBufferWithBlend can override the blend function.
        FomekRenderAPI.BlendMode bm = FomekRenderAPI.getCurrentBlendMode();
        if (bm != null && bm != FomekRenderAPI.BlendMode.DEFAULT) {
            return RenderType.entityTranslucent(texture);
        }

        return switch (renderType) {
            case "entityCutoutNoCull"        -> RenderType.entityCutoutNoCull(texture);
            case "entityTranslucent"         -> RenderType.entityTranslucent(texture);
            case "entityTranslucentEmissive" -> RenderType.entityTranslucentEmissive(texture);
            case "eyes"                      -> RenderType.eyes(texture);
            case "energySwirl"               -> RenderType.energySwirl(texture,
                                            FomekRenderAPI.getRenderTime() % 1.0f,
                                            FomekRenderAPI.getRenderTime() % 1.0f);
            case "dragonExplosionAlpha"      -> RenderType.dragonExplosionAlpha(texture);
            default                          -> RenderType.entityCutout(texture);
        };
    }
}
