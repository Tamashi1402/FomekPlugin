package __RENDERAPI_PACKAGE__;

import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * All animation classes merged into one.
 * Inner classes: Controller, Cache, File
 */
public class FomekAnimation {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // ═══════════════════════════════════════════════════════════════════════════
    // Animation Controller
    // ═══════════════════════════════════════════════════════════════════════════

    public static class Controller {

        public enum AnimationType {
                LOOP,
                HOLD_ON_LAST,
                PLAY_ONCE;

                public static AnimationType fromString(String s) {
                    if (s == null) return LOOP;
                    switch (s) {
                        case "holdOnLast": case "HOLD_ON_LAST": return HOLD_ON_LAST;
                        case "playOnce":   case "PLAY_ONCE":   return PLAY_ONCE;
                        default: return LOOP;
                    }
                }
            }

            private final String id;
            private String modelId = "";
            private String currentAnimation = "";
            private AnimationType animationType = AnimationType.LOOP;
            private long startTick = 0;

            public Controller() {
                this.id = UUID.randomUUID().toString();
            }

            public Controller(String id) {
                this.id = (id != null && !id.isEmpty()) ? id : UUID.randomUUID().toString();
            }

            // ── Getters ────────────────────────────────────────────────────────────────

            public String getId()                { return id; }
            public String getModelId()           { return modelId; }
            public String getCurrentAnimation()  { return currentAnimation; }
            public AnimationType getAnimationType() { return animationType; }
            public long getStartTick()           { return startTick; }

            // ── Setters ───────────────────────────────────────────────────────────────

            public void setModelId(String modelId) {
                this.modelId = modelId != null ? modelId : "";
            }

            public void playAnimation(String animationPath, AnimationType type, long currentTick) {
                this.currentAnimation = (animationPath != null) ? animationPath : "";
                this.animationType = (type != null) ? type : AnimationType.LOOP;
                this.startTick = currentTick;
            }

            public void stopAnimation() {
                this.currentAnimation = "";
                this.startTick = 0;
            }

            public void reset() {
                currentAnimation = "";
                animationType = AnimationType.LOOP;
                startTick = 0;
            }

            // ── State queries ───────────────────────────────────────────────────────────

            public boolean isPlaying() {
                return currentAnimation != null && !currentAnimation.isEmpty();
            }

            public float getAnimationProgress(long currentTick, float partialTick) {
                if (!isPlaying()) return 0;
                return (currentTick - startTick) + partialTick;
            }

            public boolean isFinished(long currentTick, int animLengthTicks) {
                if (!isPlaying() || animLengthTicks <= 0) return false;
                long elapsed = currentTick - startTick;
                if (animationType == AnimationType.LOOP) return false;
                return elapsed >= animLengthTicks;
            }

            // ── NBT serialization ───────────────────────────────────────────────────────

            public CompoundTag saveToNBT() {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", id);
                tag.putString("modelId", modelId);
                tag.putString("currentAnimation", currentAnimation);
                tag.putString("animationType", animationType.name());
                tag.putLong("startTick", startTick);
                return tag;
            }

            public static Controller fromNBT(CompoundTag tag) {
                if (tag == null || tag.isEmpty()) return null;
                String id = tag.getString("id");
                Controller c = new Controller(id);
                c.modelId = tag.getString("modelId");
                c.currentAnimation = tag.getString("currentAnimation");
                c.animationType = AnimationType.fromString(tag.getString("animationType"));
                c.startTick = tag.getLong("startTick");
                return c;
            }

            public void loadFromNBT(CompoundTag tag) {
                if (tag == null) return;
                this.modelId = tag.getString("modelId");
                this.currentAnimation = tag.getString("currentAnimation");
                this.animationType = AnimationType.fromString(tag.getString("animationType"));
                this.startTick = tag.getLong("startTick");
            }

            @Override
            public String toString() {
                return "AnimCtrl{" + id.substring(0, 8) + "..."
                        + ", anim=" + currentAnimation
                        + ", type=" + animationType + "}";
            }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Animation Cache
    // ═══════════════════════════════════════════════════════════════════════════

    public static class Cache {

        private static final Cache INSTANCE = new Cache();

            private final Map<String, Controller> controllers = new ConcurrentHashMap<>();

            private Cache() {}

            public static Cache getInstance() { return INSTANCE; }

            // ── Cache operations ──────────────────────────────────────────────────────

            public Controller create() {
                Controller controller = new Controller();
                controllers.put(controller.getId(), controller);
                return controller;
            }

            public Controller get(String id) {
                if (id == null || id.isEmpty()) return null;
                return controllers.get(id);
            }

            public void put(Controller controller) {
                if (controller != null) {
                    controllers.put(controller.getId(), controller);
                }
            }

            public void remove(String id) {
                if (id != null) controllers.remove(id);
            }

            // ── NBT key helper ──────────────────────────────────────────────────────────

            private static String nbtKey(String modelId) {
                return "fomek_anim_" + modelId.replace(":", "_");
            }

            // ── ItemStack (uses DataComponents.CUSTOM_DATA in NeoForge 1.21.1) ───────────

            public static Controller getFromItemStack(ItemStack stack, String modelId) {
                if (stack == null || stack.isEmpty() || modelId == null || modelId.isEmpty()) return null;
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = customData.copyTag();
                String key = nbtKey(modelId);
                if (!tag.contains(key)) return null;

                CompoundTag controllerTag = tag.getCompound(key);
                String id = controllerTag.getString("id");

                Controller controller = INSTANCE.get(id);
                if (controller == null) {
                    controller = Controller.fromNBT(controllerTag);
                    if (controller != null) INSTANCE.put(controller);
                } else {
                    controller.loadFromNBT(controllerTag);
                }
                return controller;
            }

            public static void setForItemStack(ItemStack stack, String modelId, Controller controller) {
                if (stack == null || stack.isEmpty()) return;
                String key = nbtKey(modelId);
                if (controller == null) {
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(key));
                } else {
                    controller.setModelId(modelId);
                    INSTANCE.put(controller);
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(key, controller.saveToNBT()));
                }
            }

            public static boolean hasController(ItemStack stack, String modelId) {
                if (stack == null || stack.isEmpty() || modelId == null) return false;
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                return customData.copyTag().contains(nbtKey(modelId));
            }

            // ── Entity (uses PersistentData, still works in 1.21.1) ─────────────────────

            public static Controller getFromEntity(Entity entity, String modelId) {
                if (entity == null || modelId == null || modelId.isEmpty()) return null;
                CompoundTag tag = entity.getPersistentData();
                String key = nbtKey(modelId);
                if (!tag.contains(key)) return null;

                CompoundTag controllerTag = tag.getCompound(key);
                String id = controllerTag.getString("id");

                Controller controller = INSTANCE.get(id);
                if (controller == null) {
                    controller = Controller.fromNBT(controllerTag);
                    if (controller != null) INSTANCE.put(controller);
                } else {
                    controller.loadFromNBT(controllerTag);
                }
                return controller;
            }

            public static void setForEntity(Entity entity, String modelId, Controller controller) {
                if (entity == null) return;
                String key = nbtKey(modelId);
                if (controller == null) {
                    entity.getPersistentData().remove(key);
                } else {
                    controller.setModelId(modelId);
                    INSTANCE.put(controller);
                    entity.getPersistentData().put(key, controller.saveToNBT());
                }
            }

            public static boolean hasController(Entity entity, String modelId) {
                if (entity == null || modelId == null) return false;
                return entity.getPersistentData().contains(nbtKey(modelId));
            }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Animation File
    // ═══════════════════════════════════════════════════════════════════════════

    public static class File {

        public static class Keyframe {
                public int tick;
                public float x, y, z;
                public float yaw, pitch, roll;
                public float xscale, yscale, zscale;
                public boolean visible = true;
                public int color = -1;
                public String texture = "";
                public String renderType = "";
            }

            public static class PartAnimation {
                public final List<Keyframe> keyframes = new ArrayList<>();
            }

            public static class TempVertex {
                public float x, y, z;
                public float u, v;
                public int color = -1;
            }

            public static class TempKeyframe {
                public int tick;
                public String mode = "QUADS";
                public final List<TempVertex> vertices = new ArrayList<>();
                public float x, y, z;
                public float yaw, pitch, roll;
                public float xscale = 1, yscale = 1, zscale = 1;
            }

            public static class TempShape {
                public String texture = "";
                public String renderType = "entityCutoutNoCull";
                public int color = -1;
                public int startTick = 0;
                public int endTick = 0;
                public final List<TempKeyframe> keyframes = new ArrayList<>();
            }

            private int lengthTicks = 20;
            private boolean loop = true;
            private final List<PartAnimation> parts = new ArrayList<>();
            private final List<TempShape> tempShapes = new ArrayList<>();

            private static final Map<Identifier, File> cache = new HashMap<>();

            public static File load(Identifier path) {
                if (cache.containsKey(path)) return cache.get(path);

                File file = new File();
                try {
                    Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(path);
                    if (resource.isEmpty()) {
                        LOGGER.warn("Animation file not found: " + path);
                        return null;
                    }
                    try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                        file.lengthTicks = json.has("length_ticks") ? json.get("length_ticks").getAsInt() : 20;
                        file.loop = json.has("loop") ? json.get("loop").getAsBoolean() : true;

                        if (json.has("parts")) {
                            JsonArray partsArr = json.getAsJsonArray("parts");
                            for (var partElem : partsArr) {
                                JsonObject partObj = partElem.getAsJsonObject();
                                PartAnimation pa = new PartAnimation();
                                JsonArray kfArr = partObj.getAsJsonArray("keyframes");
                                for (var kfElem : kfArr) {
                                    JsonObject kfObj = kfElem.getAsJsonObject();
                                    Keyframe kf = new Keyframe();
                                    kf.tick    = kfObj.has("tick")    ? kfObj.get("tick").getAsInt()    : 0;
                                    kf.x       = kfObj.has("x")       ? kfObj.get("x").getAsFloat()     : 0;
                                    kf.y       = kfObj.has("y")       ? kfObj.get("y").getAsFloat()     : 0;
                                    kf.z       = kfObj.has("z")       ? kfObj.get("z").getAsFloat()     : 0;
                                    kf.yaw     = kfObj.has("yaw")     ? kfObj.get("yaw").getAsFloat()   : 0;
                                    kf.pitch   = kfObj.has("pitch")   ? kfObj.get("pitch").getAsFloat() : 0;
                                    kf.roll    = kfObj.has("roll")    ? kfObj.get("roll").getAsFloat()  : 0;
                                    kf.xscale  = kfObj.has("xscale")  ? kfObj.get("xscale").getAsFloat()  : 1;
                                    kf.yscale  = kfObj.has("yscale")  ? kfObj.get("yscale").getAsFloat()  : 1;
                                    kf.zscale  = kfObj.has("zscale")  ? kfObj.get("zscale").getAsFloat()  : 1;
                                    kf.visible    = kfObj.has("visible")     ? kfObj.get("visible").getAsBoolean()    : true;
                                    kf.color      = kfObj.has("color")       ? kfObj.get("color").getAsInt()          : -1;
                                    kf.texture    = kfObj.has("texture")     ? kfObj.get("texture").getAsString()     : "";
                                    kf.renderType = kfObj.has("render_type")  ? kfObj.get("render_type").getAsString() : "";
                                    pa.keyframes.add(kf);
                                }
                                file.parts.add(pa);
                            }
                        }

                        if (json.has("temp_shapes")) {
                            JsonArray tsArr = json.getAsJsonArray("temp_shapes");
                            for (var tsElem : tsArr) {
                                JsonObject tsObj = tsElem.getAsJsonObject();
                                TempShape ts = new TempShape();
                                ts.texture    = tsObj.has("texture")     ? tsObj.get("texture").getAsString()     : "";
                                ts.renderType = tsObj.has("render_type") ? tsObj.get("render_type").getAsString() : "entityCutoutNoCull";
                                ts.color      = tsObj.has("color")       ? tsObj.get("color").getAsInt()          : -1;
                                ts.startTick  = tsObj.has("start_tick")  ? tsObj.get("start_tick").getAsInt()     : 0;
                                ts.endTick    = tsObj.has("end_tick")    ? tsObj.get("end_tick").getAsInt()       : 0;

                                JsonArray tsKfArr = tsObj.getAsJsonArray("keyframes");
                                for (var tsKfElem : tsKfArr) {
                                    JsonObject tsKfObj = tsKfElem.getAsJsonObject();
                                    TempKeyframe tkf = new TempKeyframe();
                                    tkf.tick = tsKfObj.has("tick") ? tsKfObj.get("tick").getAsInt() : 0;
                                    tkf.mode = tsKfObj.has("mode") ? tsKfObj.get("mode").getAsString() : "QUADS";
                                    tkf.x      = tsKfObj.has("x")      ? tsKfObj.get("x").getAsFloat()      : 0;
                                    tkf.y      = tsKfObj.has("y")      ? tsKfObj.get("y").getAsFloat()      : 0;
                                    tkf.z      = tsKfObj.has("z")      ? tsKfObj.get("z").getAsFloat()      : 0;
                                    tkf.yaw    = tsKfObj.has("yaw")    ? tsKfObj.get("yaw").getAsFloat()    : 0;
                                    tkf.pitch  = tsKfObj.has("pitch")  ? tsKfObj.get("pitch").getAsFloat()  : 0;
                                    tkf.roll   = tsKfObj.has("roll")   ? tsKfObj.get("roll").getAsFloat()   : 0;
                                    tkf.xscale = tsKfObj.has("xscale") ? tsKfObj.get("xscale").getAsFloat() : 1;
                                    tkf.yscale = tsKfObj.has("yscale") ? tsKfObj.get("yscale").getAsFloat() : 1;
                                    tkf.zscale = tsKfObj.has("zscale") ? tsKfObj.get("zscale").getAsFloat() : 1;

                                    if (tsKfObj.has("vertices")) {
                                        JsonArray vArr = tsKfObj.getAsJsonArray("vertices");
                                        for (var vElem : vArr) {
                                            JsonObject vObj = vElem.getAsJsonObject();
                                            TempVertex tv = new TempVertex();
                                            if (vObj.has("pos")) {
                                                JsonArray pos = vObj.getAsJsonArray("pos");
                                                tv.x = pos.size() > 0 ? pos.get(0).getAsFloat() : 0;
                                                tv.y = pos.size() > 1 ? pos.get(1).getAsFloat() : 0;
                                                tv.z = pos.size() > 2 ? pos.get(2).getAsFloat() : 0;
                                            }
                                            if (vObj.has("uv")) {
                                                JsonArray uv = vObj.getAsJsonArray("uv");
                                                tv.u = uv.size() > 0 ? uv.get(0).getAsFloat() : 0;
                                                tv.v = uv.size() > 1 ? uv.get(1).getAsFloat() : 0;
                                            }
                                            tv.color = vObj.has("color") ? vObj.get("color").getAsInt() : -1;
                                            tkf.vertices.add(tv);
                                        }
                                    }
                                    ts.keyframes.add(tkf);
                                }
                                file.tempShapes.add(ts);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load animation: " + path, e);
                    return null;
                }

                cache.put(path, file);
                return file;
            }

            public int getLengthTicks() { return lengthTicks; }
            public boolean isLoop()     { return loop; }
            public int getPartCount()   { return parts.size(); }
            public List<TempShape> getTempShapes() { return tempShapes; }
            public int getTempShapeCount() { return tempShapes.size(); }

            /**
             * Compute the effective loop length: the maximum keyframe tick
             * across all parts and temp_shapes. This is used instead of lengthTicks
             * for LOOP wrapping so the animation loops immediately after the last
             * keyframe rather than waiting for lengthTicks to elapse.
             */
            public int getEffectiveLoopLength() {
                int maxTick = 0;
                for (PartAnimation pa : parts) {
                    if (!pa.keyframes.isEmpty()) {
                        maxTick = Math.max(maxTick, pa.keyframes.get(pa.keyframes.size() - 1).tick);
                    }
                }
                for (TempShape ts : tempShapes) {
                    for (TempKeyframe tkf : ts.keyframes) {
                        maxTick = Math.max(maxTick, tkf.tick);
                    }
                }
                // Use the smaller of lengthTicks and maxTick if maxTick > 0,
                // otherwise fall back to lengthTicks.
                if (maxTick > 0 && maxTick < lengthTicks) {
                    return maxTick;
                }
                return lengthTicks;
            }

            public Keyframe getInterpolatedTransform(int partIndex, float animTime,
                    Controller.AnimationType type) {
                if (partIndex < 0 || partIndex >= parts.size()) return null;
                PartAnimation pa = parts.get(partIndex);
                if (pa.keyframes.isEmpty()) return null;

                float time = wrapTime(animTime, type, pa.keyframes.get(pa.keyframes.size() - 1).tick);
                if (time < 0) return null;

                Keyframe prev = pa.keyframes.get(0);
                Keyframe next = prev;

                for (int i = 0; i < pa.keyframes.size(); i++) {
                    Keyframe kf = pa.keyframes.get(i);
                    if (kf.tick <= time) {
                        prev = kf;
                        next = (i < pa.keyframes.size() - 1) ? pa.keyframes.get(i + 1) : kf;
                    } else {
                        next = kf;
                        break;
                    }
                }

                float t = (next.tick == prev.tick) ? 0 : (time - prev.tick) / (next.tick - prev.tick);
                t = Math.max(0, Math.min(1, t));

                Keyframe result = new Keyframe();
                result.tick = (int) time;
                result.x = lerp(prev.x, next.x, t);
                result.y = lerp(prev.y, next.y, t);
                result.z = lerp(prev.z, next.z, t);
                result.yaw = lerp(prev.yaw, next.yaw, t);
                result.pitch = lerp(prev.pitch, next.pitch, t);
                result.roll = lerp(prev.roll, next.roll, t);
                result.xscale = lerp(prev.xscale, next.xscale, t);
                result.yscale = lerp(prev.yscale, next.yscale, t);
                result.zscale = lerp(prev.zscale, next.zscale, t);
                result.visible = prev.visible;
                // FIX: Interpolate color smoothly (lerp ARGB channels) instead of snapping
                result.color = lerpColor(prev.color, next.color, t);
                result.texture = prev.texture;
                result.renderType = prev.renderType;
                return result;
            }

            public TempKeyframe getInterpolatedTempShape(int shapeIndex, float animTime,
                    Controller.AnimationType type) {
                if (shapeIndex < 0 || shapeIndex >= tempShapes.size()) return null;
                TempShape ts = tempShapes.get(shapeIndex);

                if (type == Controller.AnimationType.LOOP) {
                    int loopLen = getEffectiveLoopLength();
                    if (loopLen > 0) {
                        animTime = animTime % loopLen;
                        if (animTime < 0) animTime += loopLen;
                    }
                } else {
                    if (animTime < ts.startTick || animTime > ts.endTick) return null;
                }

                if (ts.keyframes.isEmpty()) return null;

                float time = wrapTime(animTime, type, ts.keyframes.get(ts.keyframes.size() - 1).tick);
                if (time < 0) return null;

                TempKeyframe prev = ts.keyframes.get(0);
                TempKeyframe next = prev;

                for (int i = 0; i < ts.keyframes.size(); i++) {
                    TempKeyframe kf = ts.keyframes.get(i);
                    if (kf.tick <= time) {
                        prev = kf;
                        next = (i < ts.keyframes.size() - 1) ? ts.keyframes.get(i + 1) : kf;
                    } else {
                        next = kf;
                        break;
                    }
                }

                float t = (next.tick == prev.tick) ? 0 : (time - prev.tick) / (next.tick - prev.tick);
                t = Math.max(0, Math.min(1, t));

                TempKeyframe result = new TempKeyframe();
                result.tick = (int) time;
                result.mode = prev.mode;
                result.x = lerp(prev.x, next.x, t);
                result.y = lerp(prev.y, next.y, t);
                result.z = lerp(prev.z, next.z, t);
                result.yaw = lerp(prev.yaw, next.yaw, t);
                result.pitch = lerp(prev.pitch, next.pitch, t);
                result.roll = lerp(prev.roll, next.roll, t);
                result.xscale = lerp(prev.xscale, next.xscale, t);
                result.yscale = lerp(prev.yscale, next.yscale, t);
                result.zscale = lerp(prev.zscale, next.zscale, t);

                int vCount = Math.min(prev.vertices.size(), next.vertices.size());
                for (int i = 0; i < vCount; i++) {
                    TempVertex pv = prev.vertices.get(i);
                    TempVertex nv = next.vertices.get(i);
                    TempVertex rv = new TempVertex();
                    rv.x = lerp(pv.x, nv.x, t);
                    rv.y = lerp(pv.y, nv.y, t);
                    rv.z = lerp(pv.z, nv.z, t);
                    rv.u = lerp(pv.u, nv.u, t);
                    rv.v = lerp(pv.v, nv.v, t);
                    // FIX: Interpolate vertex color smoothly instead of snapping
                    rv.color = lerpColor(pv.color, nv.color, t);
                    result.vertices.add(rv);
                }

                return result;
            }

            /**
             * Get the interpolated color for a part at the given animation time.
             * Used by renderAnimated to apply part color animation to temp shapes.
             */
            public int getPartColor(int partIndex, float animTime,
                    Controller.AnimationType type) {
                if (partIndex < 0 || partIndex >= parts.size()) return -1;
                PartAnimation pa = parts.get(partIndex);
                if (pa.keyframes.isEmpty()) return -1;

                float time = wrapTime(animTime, type, pa.keyframes.get(pa.keyframes.size() - 1).tick);
                if (time < 0) return -1;

                Keyframe prev = pa.keyframes.get(0);
                Keyframe next = prev;

                for (int i = 0; i < pa.keyframes.size(); i++) {
                    Keyframe kf = pa.keyframes.get(i);
                    if (kf.tick <= time) {
                        prev = kf;
                        next = (i < pa.keyframes.size() - 1) ? pa.keyframes.get(i + 1) : kf;
                    } else {
                        next = kf;
                        break;
                    }
                }

                float t = (next.tick == prev.tick) ? 0 : (time - prev.tick) / (next.tick - prev.tick);
                t = Math.max(0, Math.min(1, t));

                // FIX: Interpolate color smoothly
                return lerpColor(prev.color, next.color, t);
            }

            // ── Time wrapping ───────────────────────────────────────────────────────────

            private float wrapTime(float animTime, Controller.AnimationType type, int lastTick) {
                float time = animTime;
                if (type == Controller.AnimationType.LOOP) {
                    // FIX: Use effective loop length (max keyframe tick) instead of lengthTicks
                    // so the animation loops immediately after the last keyframe.
                    int loopLen = getEffectiveLoopLength();
                    if (loopLen > 0) {
                        time = time % loopLen;
                        if (time < 0) time += loopLen;
                    }
                } else if (type == Controller.AnimationType.HOLD_ON_LAST) {
                    if (time >= lastTick) time = lastTick;
                } else if (type == Controller.AnimationType.PLAY_ONCE) {
                    if (time >= lastTick) {
                        return -1;
                    }
                }
                return time;
            }

            // ── Color interpolation ───────────────────────────────────────────────────

            /**
             * Linearly interpolate between two packed ARGB colors.
             * Each channel (A, R, G, B) is interpolated independently.
             * If either color is -1 (meaning "use default"), the other wins.
             * If both are -1, returns -1.
             */
            private static int lerpColor(int c1, int c2, float t) {
                // NOTE: 0xFFFFFFFF (white) == -1 in Java signed int, same as our "no override"
                // sentinel. We can't distinguish them, so we ALWAYS interpolate the raw
                // ARGB channels. White (0xFFFFFFFF) lerped with any color = correct result,
                // and white lerped with white = white (which acts as "no tint" downstream).
                if (t <= 0) return c1;
                if (t >= 1) return c2;

                int a1 = (c1 >> 24) & 0xFF;
                int r1 = (c1 >> 16) & 0xFF;
                int g1 = (c1 >> 8) & 0xFF;
                int b1 = c1 & 0xFF;

                int a2 = (c2 >> 24) & 0xFF;
                int r2 = (c2 >> 16) & 0xFF;
                int g2 = (c2 >> 8) & 0xFF;
                int b2 = c2 & 0xFF;

                int a = Math.round(a1 + (a2 - a1) * t);
                int r = Math.round(r1 + (r2 - r1) * t);
                int g = Math.round(g1 + (g2 - g1) * t);
                int b = Math.round(b1 + (b2 - b1) * t);

                return (a << 24) | (r << 16) | (g << 8) | b;
            }

            private static float lerp(float a, float b, float t) {
                return a + (b - a) * t;
            }

    }

}
