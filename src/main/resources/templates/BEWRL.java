package __RENDERAPI_PACKAGE__;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** All BEWRL classes merged: Model, Registry, RegisterEvent, RegistrationHandler */
public class BEWRL {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public static class Model {

        public static class Part {
                public RenderAPI.Shape shape;
                public String javaModelName;  // non-null for Java model parts (shape is null)
                public String text;           // non-null for text parts (shape & javaModelName are null)
                public boolean textGlowing;   // glowing flag for text parts
                public String texture;
                public float x, y, z;
                public float yaw, pitch, roll;
                public float xscale, yscale, zscale;
                public int color;
                public String renderType;
                public BEWRL.Model childModel;  // non-null for nested BEWRL parts
                public Shader childShader;      // optional shader for child model
                public net.minecraft.world.item.ItemStack itemStack; // non-null for item parts
                public boolean itemGlowing;           // glowing flag for item parts
                /**
                 * When true, a shader's texture override is ignored for this part — it always
                 * renders with its own {@link #texture}.  Used for the flat face quads of a
                 * reconstructed item so that the item's own texture is always used as an
                 * alpha-cutout mask regardless of what shader texture is active.
                 */
                public boolean lockTexture = false;

                // ── Line-part fields (non-null linePoints => this Part is a polyline) ──
                // Used by the Trail construct to draw real GL line-strip geometry instead
                // of rotated boxes, so consecutive segments connect with zero seams.
                public float[] linePoints;   // flattened x,y,z per point (world-local to model origin)
                public int[] lineColors;     // ARGB per point, same length as linePoints/3

                public Part() {
                    this.shape = null;
                    this.javaModelName = null;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = null;
                    this.x = 0; this.y = 0; this.z = 0;
                    this.yaw = 0; this.pitch = 0; this.roll = 0;
                    this.xscale = 1; this.yscale = 1; this.zscale = 1;
                    this.color = -1;
                    this.renderType = null;
                    this.childModel = null;
                    this.childShader = null;
                    this.itemStack = null;
                    this.itemGlowing = false;
                    this.lockTexture = false;
                }

                public Part(RenderAPI.Shape shape, String texture,
                        float x, float y, float z,
                        float yaw, float pitch, float roll,
                        float xscale, float yscale, float zscale,
                        int color, String renderType) {
                    this.shape = shape;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = texture;
                    this.x = x; this.y = y; this.z = z;
                    this.yaw = yaw; this.pitch = pitch; this.roll = roll;
                    this.xscale = xscale; this.yscale = yscale; this.zscale = zscale;
                    this.color = color;
                    this.renderType = renderType;
                    this.itemStack = null;
                    this.itemGlowing = false;
                }

                // Java model constructor — shape is null, javaModelName holds the model name
                public Part(String javaModelName, String texture,
                        float x, float y, float z,
                        float yaw, float pitch, float roll,
                        float xscale, float yscale, float zscale,
                        int color, String renderType) {
                    this.shape = null;
                    this.javaModelName = javaModelName;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = texture;
                    this.x = x; this.y = y; this.z = z;
                    this.yaw = yaw; this.pitch = pitch; this.roll = roll;
                    this.xscale = xscale; this.yscale = yscale; this.zscale = zscale;
                    this.color = color;
                    this.renderType = renderType;
                    this.itemStack = null;
                    this.itemGlowing = false;
                }

                // Text part constructor — shape & javaModelName are null
                public Part(String text, boolean glowing,
                        float x, float y, float z,
                        float yaw, float pitch, float roll,
                        float xscale, float yscale, float zscale,
                        int color) {
                    this.shape = null;
                    this.javaModelName = null;
                    this.text = text;
                    this.textGlowing = glowing;
                    this.texture = null;
                    this.x = x; this.y = y; this.z = z;
                    this.yaw = yaw; this.pitch = pitch; this.roll = roll;
                    this.xscale = xscale; this.yscale = yscale; this.zscale = zscale;
                    this.color = color;
                    this.renderType = null;
                    this.itemStack = null;
                    this.itemGlowing = false;
                }

                // Child BEWRL model constructor — nests a BEWRL model as a part
                public Part(BEWRL.Model childModel, Shader shader,
                        float x, float y, float z,
                        float yaw, float pitch, float roll,
                        float xscale, float yscale, float zscale) {
                    this.shape = null;
                    this.javaModelName = null;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = null;
                    this.childModel = childModel;
                    this.childShader = shader;
                    this.x = x; this.y = y; this.z = z;
                    this.yaw = yaw; this.pitch = pitch; this.roll = roll;
                    this.xscale = xscale; this.yscale = yscale; this.zscale = zscale;
                    this.color = -1;
                    this.renderType = null;
                    this.itemStack = null;
                    this.itemGlowing = false;
                }

                // Item part constructor — renders an ItemStack as a BEWRL part
                public Part(net.minecraft.world.item.ItemStack itemStack, boolean glowing,
                        float x, float y, float z,
                        float yaw, float pitch, float roll,
                        float xscale, float yscale, float zscale) {
                    this.shape = null;
                    this.javaModelName = null;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = null;
                    this.itemStack = itemStack;
                    this.itemGlowing = glowing;
                    this.x = x; this.y = y; this.z = z;
                    this.yaw = yaw; this.pitch = pitch; this.roll = roll;
                    this.xscale = xscale; this.yscale = yscale; this.zscale = zscale;
                    this.color = -1;
                    this.renderType = null;
                    this.childModel = null;
                    this.childShader = null;
                }

                // Line-part constructor — a polyline drawn as real GL_LINES geometry.
                // points: flattened x,y,z triples; colors: ARGB per point (same count).
                public Part(float[] points, int[] colors) {
                    this.shape = null;
                    this.javaModelName = null;
                    this.text = null;
                    this.textGlowing = false;
                    this.texture = null;
                    this.x = 0; this.y = 0; this.z = 0;
                    this.yaw = 0; this.pitch = 0; this.roll = 0;
                    this.xscale = 1; this.yscale = 1; this.zscale = 1;
                    this.color = -1;
                    this.renderType = null;
                    this.childModel = null;
                    this.childShader = null;
                    this.itemStack = null;
                    this.itemGlowing = false;
                    this.linePoints = points;
                    this.lineColors = colors;
                }

                public Part copy() {
                    if (linePoints != null) {
                        return new Part(linePoints.clone(), lineColors != null ? lineColors.clone() : null);
                    }
                    if (childModel != null) {
                        return new Part(childModel.copy(), childShader, x, y, z, yaw, pitch, roll,
                                xscale, yscale, zscale);
                    }
                    if (itemStack != null) {
                        return new Part(itemStack, itemGlowing, x, y, z, yaw, pitch, roll,
                                xscale, yscale, zscale);
                    }
                    if (text != null) {
                        return new Part(text, textGlowing, x, y, z, yaw, pitch, roll,
                                xscale, yscale, zscale, color);
                    }
                    if (javaModelName != null) {
                        return new Part(javaModelName, texture, x, y, z, yaw, pitch, roll,
                                xscale, yscale, zscale, color, renderType);
                    }
                    return new Part(shape, texture, x, y, z, yaw, pitch, roll,
                            xscale, yscale, zscale, color, renderType);
                }
            }

            private final List<Part> parts = new ArrayList<>();

            /**
             * The ItemStack this model was reconstructed from, if any.
             * Set by RenderAPI.reconstructItemAsBEWRL so that addBEWRLChildPart
             * can re-reconstruct with the correct rimOnly flag at the time the model is added.
             */
            public net.minecraft.world.item.ItemStack sourceItemStack = null;

            public void addPart(RenderAPI.Shape shape, String texture,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale, int color, String renderType) {
                parts.add(new Part(shape, texture, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale, color, renderType));
            }

            /**
             * Like {@link #addPart} but marks the part with {@code lockTexture=true},
             * preventing any shader's texture override from replacing this part's texture.
             * Used for reconstructed item face quads so the item texture always acts as
             * the alpha-cutout mask even when a custom shader texture is active.
             */
            public void addPartLocked(RenderAPI.Shape shape, String texture,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale, int color, String renderType) {
                Part p = new Part(shape, texture, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale, color, renderType);
                p.lockTexture = true;
                parts.add(p);
            }

            public void addJavaModelPart(String javaModelName, String texture,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale, int color, String renderType) {
                parts.add(new Part(javaModelName, texture, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale, color, renderType));
            }

            public void addChildPart(BEWRL.Model childModel, Shader shader,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale) {
                parts.add(new Part(childModel, shader, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale));
            }

            public void addTextPart(String text, boolean glowing,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale, int color) {
                parts.add(new Part(text, glowing, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale, color));
            }

            public void addItemPart(net.minecraft.world.item.ItemStack itemStack, boolean glowing,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale) {
                parts.add(new Part(itemStack, glowing, x, y, z, yaw, pitch, roll,
                        xscale, yscale, zscale));
            }

            /**
             * Add a polyline part — a continuous GL_LINES strip drawn between the given
             * points (flattened x,y,z triples), with a per-point ARGB color.  Because
             * consecutive line segments share exact endpoint coordinates, they connect
             * with zero visual seams — unlike independently-rotated box segments.
             * Used by the Trail construct for smooth flash-trail geometry.
             */
            public void addLinePart(float[] points, int[] colors) {
                if (points == null || points.length < 6) return; // need at least 2 points
                parts.add(new Part(points, colors));
            }

            public boolean isEmpty() { return parts.isEmpty(); }
            public List<Part> getParts() { return parts; }
            public int getPartCount() { return parts.size(); }

            public Model copy() {
                Model copy = new Model();
                for (Part part : parts) {
                    copy.parts.add(part.copy());
                }
                return copy;
            }

            public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float scale,
                    int packedLight, int packedOverlay) {
                if (parts.isEmpty() || bufferSource == null) return;

                // ── Apply model-level transform so yaw/pitch/roll actually rotate the model ──
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (scale != 1) poseStack.scale(scale, scale, scale);

                for (Part part : parts) {
                    if (part.linePoints != null) {
                        renderLinePart(part, poseStack, bufferSource);
                    } else if (part.text != null) {
                        renderTextPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                part.color, part.textGlowing,
                                packedLight);
                    } else if (part.javaModelName != null) {
                        renderJavaModelPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                part.texture, part.renderType,
                                packedLight, packedOverlay);
                    } else if (part.childModel != null) {
                        // ── Render nested child BEWRL model ──
                        poseStack.pushPose();
                        poseStack.translate(part.x, part.y, part.z);
                        if (part.yaw != 0) poseStack.mulPose(Axis.YP.rotationDegrees(part.yaw));
                        if (part.pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(part.pitch));
                        if (part.roll != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(part.roll));
                        float cs = part.xscale; // use x as uniform scale for child
                        if (cs != 1) {
                            // Scale around item center (0.5, 0.5, 0.5) so scaling stays centered.
                            // Without this, scaling pushes 0-1 vertices away from origin asymmetrically.
                            poseStack.translate(0.5f, 0.5f, 0.5f);
                            poseStack.scale(cs, part.yscale, part.zscale);
                            poseStack.translate(-0.5f, -0.5f, -0.5f);
                        }
                        if (part.childShader != null) {
                            int childLight = part.childShader.isGlowing()
                                    ? net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT
                                    : packedLight;
                            part.childShader.apply();
                            part.childModel.renderWithShader(poseStack, bufferSource,
                                0, 0, 0, 0, 0, 0, 1,
                                part.childShader, childLight, packedOverlay);
                            part.childShader.restore();
                        } else {
                            part.childModel.render(poseStack, bufferSource,
                                0, 0, 0, 0, 0, 0, 1,
                                packedLight, packedOverlay);
                        }
                        poseStack.popPose();
                    } else if (part.itemStack != null) {
                        renderItemPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                packedLight, packedOverlay, null);
                    } else {
                        if (part.shape == null || part.shape.isEmpty()) continue;
                        renderPart(part.shape, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                part.color, part.texture, part.renderType,
                                packedLight, packedOverlay);
                    }
                }

                poseStack.popPose();
            }

            public void renderAnimated(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float scale,
                    int packedLight, int packedOverlay,
                    Animation.Controller controller,
                    float gameTime, float partialTick) {

                if (bufferSource == null) return;
                if (controller == null || !controller.isPlaying()) {
                    render(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            packedLight, packedOverlay);
                    return;
                }

                // ── Apply model-level transform ──
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (scale != 1) poseStack.scale(scale, scale, scale);
                float _origX = x, _origY = y, _origZ = z, _origScale = scale;
                x = 0; y = 0; z = 0; scale = 1; // Parts now use local coords

                Animation.File animFile = null;
                float animTime = 0;
                try {
                    Identifier animPath = Identifier.parse(controller.getCurrentAnimation());
                    animFile = Animation.File.load(animPath);
                    animTime = controller.getAnimationProgress((long) gameTime, partialTick);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load animation: " + controller.getCurrentAnimation());
                }

                if (animFile == null) {
                    poseStack.popPose();
                    x = _origX; y = _origY; z = _origZ; scale = _origScale;
                    render(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            packedLight, packedOverlay);
                    return;
                }

                Animation.Controller.AnimationType animType = controller.getAnimationType();

                if (animType == Animation.Controller.AnimationType.PLAY_ONCE
                        && controller.isFinished((long) gameTime, animFile.getLengthTicks())) {
                    controller.stopAnimation();
                    poseStack.popPose();
                    x = _origX; y = _origY; z = _origZ; scale = _origScale;
                    render(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            packedLight, packedOverlay);
                    return;
                }

                // ── Render existing parts with animation ──────────────────────────────

                for (int i = 0; i < parts.size(); i++) {
                    Part part = parts.get(i);

                    // Text parts: render at static position
                    if (part.text != null) {
                        renderTextPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                part.color, part.textGlowing,
                                packedLight);
                        continue;
                    }

                    // Java model parts: render at static position (no animation support yet)
                    if (part.javaModelName != null) {
                        renderJavaModelPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                part.texture, part.renderType,
                                packedLight, packedOverlay);
                        continue;
                    }

                    // Item parts: render at static position (no animation support yet)
                    if (part.itemStack != null) {
                        renderItemPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                packedLight, packedOverlay, null);
                        continue;
                    }

                    if (part.shape == null || part.shape.isEmpty()) continue;

                    Animation.File.Keyframe kf = animFile.getInterpolatedTransform(i, animTime, animType);
                    if (kf == null) {
                        renderPart(part.shape, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                part.color, part.texture, part.renderType,
                                packedLight, packedOverlay);
                        continue;
                    }

                    if (!kf.visible) continue;

                    int partColor = (kf.color != -1) ? kf.color : part.color;
                    String partTex = (kf.texture != null && !kf.texture.isEmpty()) ? kf.texture : part.texture;
                    String partRT  = (kf.renderType != null && !kf.renderType.isEmpty()) ? kf.renderType : part.renderType;

                    renderPart(part.shape, poseStack, bufferSource,
                            x + kf.x * scale, y + kf.y * scale, z + kf.z * scale,
                            kf.yaw, kf.pitch, kf.roll,
                            kf.xscale * scale, kf.yscale * scale, kf.zscale * scale,
                            partColor, partTex, partRT,
                            packedLight, packedOverlay);
                }

                // ── Render temporary shapes (v2) ───────────────────────────────────────
                // FIX: When a temp_shape replaces a hidden part, apply the part's color
                // animation to the temp_shape so color keyframes still work.

                int tempCount = animFile.getTempShapeCount();
                for (int i = 0; i < tempCount; i++) {
                    Animation.File.TempKeyframe tkf = animFile.getInterpolatedTempShape(i, animTime, animType);
                    if (tkf == null || tkf.vertices.isEmpty()) continue;

                    Animation.File.TempShape ts = animFile.getTempShapes().get(i);

                    // FIX: Look up the corresponding part's color at this animation time.
                    // Temp shapes replace hidden parts, so we inherit their color animation.
                    int partColorOverride = -1;
                    if (i < parts.size()) {
                        partColorOverride = animFile.getPartColor(i, animTime, animType);
                    }

                    RenderAPI.Shape tempShape = new RenderAPI.Shape();
                    VertexFormat.Mode mode = tkf.mode.equalsIgnoreCase("TRIANGLES")
                            ? VertexFormat.Mode.TRIANGLES : VertexFormat.Mode.QUADS;
                    tempShape.begin(mode, true);

                    for (Animation.File.TempVertex tv : tkf.vertices) {
                        // Priority: vertex color > part color override > temp shape color
                        int vColor;
                        if (tv.color != -1) {
                            vColor = tv.color;
                        } else if (partColorOverride != -1) {
                            vColor = partColorOverride;
                        } else {
                            vColor = (ts.color != -1) ? ts.color : -1;
                        }
                        tempShape.addVertexUV(tv.x, tv.y, tv.z, tv.u, tv.v, vColor);
                    }
                    tempShape.end();

                    String shapeTexture = (ts.texture != null && !ts.texture.isEmpty())
                            ? ts.texture : "minecraft:textures/misc/white.png";
                    String shapeRT = (ts.renderType != null && !ts.renderType.isEmpty())
                            ? ts.renderType : "entityCutoutNoCull";

                    // Use part color override as shape color if vertices didn't carry it
                    int shapeColor = (partColorOverride != -1) ? partColorOverride
                            : ((ts.color != -1) ? ts.color : -1);

                    renderPart(tempShape, poseStack, bufferSource,
                            tkf.x, tkf.y, tkf.z,
                            tkf.yaw, tkf.pitch, tkf.roll,
                            tkf.xscale, tkf.yscale, tkf.zscale,
                            shapeColor, shapeTexture, shapeRT,
                            packedLight, packedOverlay);
                }

                poseStack.popPose();
                x = _origX; y = _origY; z = _origZ; scale = _origScale;
            }


            // ── Shader-aware rendering ──────────────────────────────────────────────

            /**
             * Render with a Shader — overrides render type, color, light, texture.
             */
            public void renderWithShader(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float scale,
                    Shader shader, int packedLight, int packedOverlay) {
                if (parts.isEmpty()) return;

                // ── Check for custom GLSL ──────────────────────────────────────────
                String vertSrc = shader.getVertexShaderSource();
                String fragSrc = shader.getFragmentShaderSource();
                boolean hasGlsl = vertSrc != null && fragSrc != null
                    && !vertSrc.trim().isEmpty() && !fragSrc.trim().isEmpty();

                if (hasGlsl) {
                    // Compile (or retrieve cached) GL program
                    int programId = Shader.Manager.getOrCreateProgram(vertSrc, fragSrc);
                    if (programId == 0) {
                        // Compilation failed — fall through to standard rendering
                        hasGlsl = false;
                    } else {
                        // GLSL path: renderWithGlslProgram applies its own model transform,
                        // so we pass the original values directly.
                        renderWithGlslProgram(poseStack, bufferSource, programId, shader, x, y, z,
                            yaw, pitch, roll, scale, packedLight, packedOverlay);
                        return;
                    }
                }

                // ── Standard rendering (no custom GLSL) ───────────────────────────
                if (bufferSource == null) return;

                // Apply model-level transform
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (scale != 1) poseStack.scale(scale, scale, scale);

                for (Part part : parts) {
                    if (part.linePoints != null) {
                        renderLinePart(part, poseStack, bufferSource);
                    } else if (part.text != null) {
                        renderTextPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                shader.applyColor(part.color), part.textGlowing,
                                packedLight);
                    } else if (part.javaModelName != null) {
                        String rt = (shader.getRenderType() != null && !shader.getRenderType().isEmpty())
                                ? shader.getRenderType() : part.renderType;
                        String tex = (shader.getTexture() != null)
                                ? shader.getTexture() : part.texture;
                        renderJavaModelPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                tex, rt, packedLight, packedOverlay);
                    } else if (part.childModel != null) {
                        // ── Render nested child BEWRL model with its own shader ──
                        // Child parts carry their own shader; the parent shader is NOT
                        // applied to child geometry (the child shader takes priority).
                        poseStack.pushPose();
                        poseStack.translate(part.x, part.y, part.z);
                        if (part.yaw != 0) poseStack.mulPose(Axis.YP.rotationDegrees(part.yaw));
                        if (part.pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(part.pitch));
                        if (part.roll != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(part.roll));
                        if (part.xscale != 1 || part.yscale != 1 || part.zscale != 1) {
                            // Scale around item center (0.5, 0.5, 0.5) so scaling stays centered.
                            poseStack.translate(0.5f, 0.5f, 0.5f);
                            poseStack.scale(part.xscale, part.yscale, part.zscale);
                            poseStack.translate(-0.5f, -0.5f, -0.5f);
                        }
                        if (part.childShader != null) {
                            // Check child shader's glow flag — if glowing, use FULL_BRIGHT
                            int childLight = part.childShader.isGlowing()
                                    ? net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT
                                    : packedLight;
                            part.childShader.apply();
                            part.childModel.renderWithShader(poseStack, bufferSource,
                                0, 0, 0, 0, 0, 0, 1,
                                part.childShader, childLight, packedOverlay);
                            part.childShader.restore();
                        } else {
                            // No child shader — use the parent shader
                            int parentLight = shader.isGlowing()
                                    ? net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT
                                    : packedLight;
                            part.childModel.renderWithShader(poseStack, bufferSource,
                                0, 0, 0, 0, 0, 0, 1,
                                shader, parentLight, packedOverlay);
                        }
                        poseStack.popPose();
                    } else if (part.itemStack != null) {
                        renderItemPart(part, poseStack, bufferSource,
                                part.x, part.y, part.z,
                                part.yaw, part.pitch, part.roll,
                                part.xscale, part.yscale, part.zscale,
                                packedLight, packedOverlay, shader);
                    } else {
                        if (part.shape == null || part.shape.isEmpty()) continue;
                        // Override packedLight if this shader is glowing
                        int effectiveLight = shader.isGlowing()
                                ? net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT
                                : packedLight;
                        String rt = (shader.getRenderType() != null && !shader.getRenderType().isEmpty())
                                ? shader.getRenderType() : part.renderType;
                        // lockTexture=true: this part's texture is locked and cannot be
                        // overridden by the shader.  Used for alpha-mask parts where the
                        // item's own texture must always be preserved.
                        // (Note: reconstructed item models no longer use lockTexture —
                        //  face quads are now per-pixel geometry in the same shape as
                        //  edge quads, all with lockTexture=false for uniform swirl.)
                        String tex = (!part.lockTexture && shader.getTexture() != null)
                                ? shader.getTexture() : part.texture;
                        int col = shader.applyColor(part.color);
                        if ("swirl".equals(rt) || "energySwirl".equals(rt)) {
                            // ── Swirl rendering path (swirl + energySwirl) ──────────
                            //
                            // swirl:  Our custom swirl system.  Uses createSwirlRenderType
                            //   which allows custom blend modes (set via enableBlending() or
                            //   shader.setSwirlBlendMode()).  UV scroll speeds come from the shader.
                            //
                            // energySwirl: Vanilla MC swirl.  Fixed additive blend, no customization.
                            //
                            // lockTexture=true  → locked part.  UV offsets are 0.0 (no scroll).
                            // lockTexture=false → shader-overridable part.  UV offsets animate.
                            float xOff = part.lockTexture ? 0.0f : (RenderAPI.getRenderTime() * shader.getSwirlXSpeed()) % 1.0f;
                            float zOff = part.lockTexture ? 0.0f : (RenderAPI.getRenderTime() * shader.getSwirlZSpeed()) % 1.0f;
                            net.minecraft.resources.Identifier swirlTex =
                                net.minecraft.resources.Identifier.parse(tex != null ? tex : "minecraft:textures/misc/white.png");
                            net.minecraft.client.renderer.RenderType customRt;
                            if ("swirl".equals(rt)) {
                                // Resolve blend mode: shader's swirlBlendMode → global currentBlendMode → ADDITION
                                RenderAPI.BlendMode blendMode = null;
                                String sbm = shader.getSwirlBlendMode();
                                if (sbm != null && !sbm.isEmpty()) {
                                    try { blendMode = RenderAPI.BlendMode.valueOf(sbm); }
                                    catch (IllegalArgumentException ignored) {}
                                }
                                customRt = RenderAPI.Shape.createSwirlRenderType(
                                    swirlTex, xOff, zOff, blendMode);
                            } else {
                                customRt = net.minecraft.client.renderer.RenderType.energySwirl(swirlTex, xOff, zOff);
                            }
                            renderPartWithRenderType(part.shape, poseStack, bufferSource,
                                    part.x, part.y, part.z,
                                    part.yaw, part.pitch, part.roll,
                                    part.xscale, part.yscale, part.zscale,
                                    col, customRt, effectiveLight, packedOverlay);
                        } else {
                            renderPart(part.shape, poseStack, bufferSource,
                                    part.x, part.y, part.z,
                                    part.yaw, part.pitch, part.roll,
                                    part.xscale, part.yscale, part.zscale,
                                    col, tex, rt, effectiveLight, packedOverlay);
                        }
                    }
                }

                poseStack.popPose();
            }

            /**
             * Render all parts using a raw GL program via direct OpenGL calls.
             *
             * This bypasses MC's MultiBufferSource/RenderType system entirely.
             * The reason: MC's endBatch() calls renderType.setupRenderState()
             * which calls RenderSystem.setShader(MC's shader) → glUseProgram(MC's program),
             * overriding our custom program. By using raw GL (VAO/VBO + glDrawArrays),
             * our glUseProgram stays active and the geometry is drawn with OUR shader.
             */
            private void renderWithGlslProgram(PoseStack poseStack, MultiBufferSource bufferSource, int programId, Shader shader,
                    float x, float y, float z, float yaw, float pitch, float roll,
                    float scale, int packedLight, int packedOverlay) {

                // ── Apply global model transform ────────────────────────────────────
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (scale != 1) poseStack.scale(scale, scale, scale);

                // ── Bind our shader and upload matrices ─────────────────────────────
                GL20.glUseProgram(programId);

                // Strategy: bake the full transform (view × global × part) into each vertex position.
                // We pass the combined matrix as ModelViewMat and identity for the per-vertex part,
                // so the shader: gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)
                // works correctly with Position already in view space.
                // globalPose = poseStack.last().pose() = the item-space model transform
                // viewMatrix = RenderSystem.getModelViewMatrix() = camera/view transform
                Matrix4f globalPose = new Matrix4f(poseStack.last().pose());
                Matrix4f viewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
                // We'll bake viewMatrix * globalPose * partTransform into vertex positions below.
                // ModelViewMat uniform = identity (positions already in clip-ready view space).
                Matrix4f identityMV = new Matrix4f(); // identity
                Matrix4f projection = RenderSystem.getProjectionMatrix();
                Shader.Manager.applyMatrices(programId, identityMV, projection);

                // Upload custom uniforms (uTime, uIntensity, etc.)
                Shader.Manager.applyUniforms(programId, shader);

                // ── Query attribute locations from our program ──────────────────────
                int posLoc   = Shader.Manager.getAttribLocation(programId, "Position");
                int colorLoc = Shader.Manager.getAttribLocation(programId, "Color");
                int uv0Loc   = Shader.Manager.getAttribLocation(programId, "UV0");

                // ── Render text parts normally (can't be baked into GLSL VAO) ───────
                for (Part part : parts) {
                    if (part.text == null) continue;
                    renderTextPart(part, poseStack, bufferSource,
                            x + part.x * scale, y + part.y * scale, z + part.z * scale,
                            part.yaw, part.pitch, part.roll,
                            part.xscale * scale, part.yscale * scale, part.zscale * scale,
                            shader.applyColor(part.color), part.textGlowing,
                            packedLight);
                }

                // ── Collect vertex data from all parts ──────────────────────────────
                // Each vertex stores: Position(3f) + Color(4f) + UV0(2f) = 9 floats
                // Only attributes that the shader actually declares are uploaded.
                int floatsPerVertex = 3; // Position always
                boolean hasColor = colorLoc >= 0;
                boolean hasUv0   = uv0Loc >= 0;
                if (hasColor) floatsPerVertex += 4;
                if (hasUv0)   floatsPerVertex += 2;

                // We convert quads to triangles (2 tris per quad) since GL_QUADS
                // is deprecated in core profile.
                List<float[]> rawVerts = new ArrayList<>();
                List<VertexFormat.Mode> vertModes = new ArrayList<>();

                List<float[]> lineVerts = new ArrayList<>();

                for (Part part : parts) {
                    // Java model parts and text parts can't be baked into vertex arrays — skip in GLSL mode
                    if (part.javaModelName != null || part.text != null) continue;

                    // ── Line parts: expand into camera-facing quads ──
                    // Instead of GL_LINES (clamped to 1px in core profile), expand
                    // each segment into a billboard quad (2 triangles) whose width
                    // is proportional to the vertex alpha. High alpha (new, near
                    // entity) = thick; low alpha (old tail) = thin, tapering to 0.
                    if (part.linePoints != null) {
                        float[] pts = part.linePoints;
                        int[] cols = part.lineColors;
                        int pointCount = pts.length / 3;
                        if (pointCount < 2) continue;
                        Matrix4f lineMat = new Matrix4f(viewMatrix).mul(globalPose);
                        int count = 3 + (hasColor ? 4 : 0) + (hasUv0 ? 2 : 0);

                        // Precompute transformed positions + colors + alpha for all points
                        Vector3f[] tpos = new Vector3f[pointCount];
                        float[][] tcol = new float[pointCount][4];
                        float[] talpha = new float[pointCount];
                        for (int li = 0; li < pointCount; li++) {
                            Vector3f lpos = new Vector3f(pts[li * 3], pts[li * 3 + 1], pts[li * 3 + 2]);
                            lineMat.transformPosition(lpos);
                            tpos[li] = lpos;
                            int lc = (cols != null && li < cols.length) ? cols[li] : -1;
                            float lr = ((lc >> 16) & 0xFF) / 255f;
                            float lg = ((lc >> 8) & 0xFF) / 255f;
                            float lb = (lc & 0xFF) / 255f;
                            float la = ((lc >> 24) & 0xFF) / 255f;
                            if (la == 0 && lc == -1) la = 1f;
                            tcol[li][0] = lr; tcol[li][1] = lg; tcol[li][2] = lb; tcol[li][3] = la;
                            talpha[li] = la;
                        }

                        // Base half-width in view-space units (tunable: 0.08 ~ 8cm at full alpha)
                        float baseWidth = 0.15f;

                        // For each segment, emit a camera-facing quad (2 triangles)
                        for (int seg = 0; seg < pointCount - 1; seg++) {
                            Vector3f p1 = tpos[seg];
                            Vector3f p2 = tpos[seg + 1];

                            // Segment direction in view space
                            float dx = p2.x - p1.x;
                            float dy = p2.y - p1.y;
                            float dz = p2.z - p1.z;
                            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (len < 0.0001f) continue;

                            // Camera-facing perpendicular in view space.
                            // Camera looks down -Z, so cross(dir, (0,0,-1)) = (-ny, nx, 0).
                            float nx = dx / len;
                            float ny = dy / len;
                            float perpX = -ny;
                            float perpY = nx;
                            float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY);
                            if (perpLen < 0.0001f) {
                                perpX = 0f; perpY = 1f;
                            } else {
                                perpX /= perpLen;
                                perpY /= perpLen;
                            }

                            // Width at each endpoint, proportional to alpha
                            float w1 = baseWidth * talpha[seg];
                            float w2 = baseWidth * talpha[seg + 1];

                            // Quad corners (offset perpendicular to line in screen space)
                            float[][] quad = {
                                {p1.x + perpX * w1, p1.y + perpY * w1, p1.z},
                                {p1.x - perpX * w1, p1.y - perpY * w1, p1.z},
                                {p2.x + perpX * w2, p2.y + perpY * w2, p2.z},
                                {p2.x - perpX * w2, p2.y - perpY * w2, p2.z},
                            };
                            // Two triangles: A-B-C  and  A-C-D
                            int[] triIdx = {0, 1, 2, 0, 2, 3};
                            for (int ti : triIdx) {
                                int li = (ti < 2) ? seg : seg + 1;
                                float[] le = new float[count];
                                int li2 = 0;
                                le[li2++] = quad[ti][0]; le[li2++] = quad[ti][1]; le[li2++] = quad[ti][2];
                                if (hasColor) {
                                    le[li2++] = tcol[li][0]; le[li2++] = tcol[li][1];
                                    le[li2++] = tcol[li][2]; le[li2++] = tcol[li][3];
                                }
                                if (hasUv0) {
                                    // Store the CENTERLINE position (view-space X and Z),
                                    // NOT the offset corner position. Both corners of the
                                    // same cross-section (left/right edge of the ribbon)
                                    // share this same value, so any jitter the vertex
                                    // shader derives from it stays IDENTICAL across the
                                    // ribbon's width — the whole slice sways together
                                    // instead of the two edges flapping independently
                                    // (which produced jagged/spiky triangles).
                                    le[li2++] = tpos[li].x; le[li2++] = tpos[li].z;
                                }
                                rawVerts.add(le);
                            }
                        }
                        continue;
                    }

                    if (part.shape == null || part.shape.isEmpty()) continue;

                    // Build the full transform for this part's vertices:
                    // fullMat = view × globalPose × partLocalTransform
                    // Vertices stored in the VAO will already be in view-space,
                    // so the shader just applies ProjMat to them.
                    Matrix4f partLocalMat = new Matrix4f();
                    partLocalMat.translate(part.x * scale, part.y * scale, part.z * scale);
                    if (part.yaw   != 0) partLocalMat.rotateY((float) Math.toRadians(part.yaw));
                    if (part.pitch != 0) partLocalMat.rotateX((float) Math.toRadians(part.pitch));
                    if (part.roll  != 0) partLocalMat.rotateZ((float) Math.toRadians(part.roll));
                    if (part.xscale != 1 || part.yscale != 1 || part.zscale != 1) {
                        partLocalMat.scale(part.xscale * scale, part.yscale * scale, part.zscale * scale);
                    }
                    // partMatrix = view × globalPose × partLocal (full view-space transform)
                    Matrix4f partMatrix = new Matrix4f(viewMatrix).mul(globalPose).mul(partLocalMat);

                    // Compute part color
                    int col = shader.applyColor(part.color);
                    float r = ((col >> 16) & 0xFF) / 255f;
                    float g = ((col >> 8) & 0xFF) / 255f;
                    float b = (col & 0xFF) / 255f;
                    float a = ((col >> 24) & 0xFF) / 255f;
                    if (a == 0) a = 1f;

                    List<RenderAPI.Shape.VertexData> shapeVerts = part.shape.getVertices();
                    VertexFormat.Mode mode = part.shape.getMode();

                    if (mode == VertexFormat.Mode.QUADS && shapeVerts.size() >= 4) {
                        // Convert each quad (4 verts) → 2 triangles (6 verts)
                        for (int qi = 0; qi + 3 < shapeVerts.size(); qi += 4) {
                            // Triangle 1: qi, qi+1, qi+2
                            // Triangle 2: qi, qi+2, qi+3
                            int[] triIndices = {qi, qi+1, qi+2,  qi, qi+2, qi+3};
                            for (int ti : triIndices) {
                                addVertexToList(shapeVerts.get(ti), partMatrix, r, g, b, a,
                                        hasColor, hasUv0, rawVerts);
                            }
                        }
                    } else {
                        // TRIANGLES or other — use vertices as-is
                        for (RenderAPI.Shape.VertexData v : shapeVerts) {
                            addVertexToList(v, partMatrix, r, g, b, a,
                                    hasColor, hasUv0, rawVerts);
                        }
                    }

                }

                poseStack.popPose();

                if (rawVerts.isEmpty() && lineVerts.isEmpty()) {
                    GL20.glUseProgram(0);
                    return;
                }

                int vertexCount = rawVerts.size();

                // ── Build FloatBuffer for shapes ─────────────────────────────────────
                FloatBuffer buf = MemoryUtil.memAllocFloat(Math.max(1, vertexCount * floatsPerVertex));
                for (float[] v : rawVerts) {
                    buf.put(v);
                }
                buf.flip();

                // ── Set up GL state for rendering ────────────────────────────────────
                // Save current state
                boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
                boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

                // Enable blending for translucent rendering, keep depth test
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.enableDepthTest();
                // Disable culling so we see both sides of the geometry
                RenderSystem.disableCull();

                // ── Create VAO + VBO and draw ────────────────────────────────────────
                int vao = GL30.glGenVertexArrays();
                int vbo = GL15.glGenBuffers();

                GL30.glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);

                int stride = floatsPerVertex * 4; // bytes per vertex
                int offset = 0;

                if (posLoc >= 0) {
                    GL20.glEnableVertexAttribArray(posLoc);
                    GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, stride, offset);
                    offset += 3 * 4;
                }
                if (hasColor) {
                    GL20.glEnableVertexAttribArray(colorLoc);
                    GL20.glVertexAttribPointer(colorLoc, 4, GL11.GL_FLOAT, false, stride, offset);
                    offset += 4 * 4;
                }
                if (hasUv0) {
                    GL20.glEnableVertexAttribArray(uv0Loc);
                    GL20.glVertexAttribPointer(uv0Loc, 2, GL11.GL_FLOAT, false, stride, offset);
                }

                // Draw shapes!
                if (vertexCount > 0) {
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
                }

                // ── Draw line parts (GL_LINES) with the same shader ──
                if (!lineVerts.isEmpty()) {
                    if (vertexCount > 0) MemoryUtil.memFree(buf);
                    int lineFloatCount = 0;
                    for (float[] lv : lineVerts) lineFloatCount += lv.length;
                    FloatBuffer lineBuf = MemoryUtil.memAllocFloat(lineFloatCount);
                    for (float[] lv : lineVerts) lineBuf.put(lv);
                    lineBuf.flip();

                    int lineVbo = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVbo);
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, lineBuf, GL15.GL_STATIC_DRAW);

                    int lineStride = floatsPerVertex * 4;
                    int lineOff = 0;
                    if (posLoc >= 0) {
                        GL20.glEnableVertexAttribArray(posLoc);
                        GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, lineStride, lineOff);
                        lineOff += 3 * 4;
                    }
                    if (hasColor) {
                        GL20.glEnableVertexAttribArray(colorLoc);
                        GL20.glVertexAttribPointer(colorLoc, 4, GL11.GL_FLOAT, false, lineStride, lineOff);
                        lineOff += 4 * 4;
                    }
                    if (hasUv0) {
                        GL20.glEnableVertexAttribArray(uv0Loc);
                        GL20.glVertexAttribPointer(uv0Loc, 2, GL11.GL_FLOAT, false, lineStride, lineOff);
                    }

                    // Widen + smooth the line a bit so additive-blended overlaps at
                    // high-alpha (new/opaque) segments read as a soft glow/halo instead
                    // of a razor-thin 1px line. Not real bloom, but combined with
                    // ADDITION blending it approximates the "glow going out from trail"
                    // effect cheaply. Some core-profile drivers clamp width to 1.0 —
                    // this degrades gracefully to a plain thin line on those.
                    GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glLineWidth(2.5f);

                    GL11.glDrawArrays(GL11.GL_LINES, 0, lineVerts.size());

                    GL11.glLineWidth(1.0f);
                    GL11.glDisable(GL11.GL_LINE_SMOOTH);

                    if (posLoc >= 0)   GL20.glDisableVertexAttribArray(posLoc);
                    if (hasColor)      GL20.glDisableVertexAttribArray(colorLoc);
                    if (hasUv0)        GL20.glDisableVertexAttribArray(uv0Loc);

                    GL15.glDeleteBuffers(lineVbo);
                    MemoryUtil.memFree(lineBuf);
                } else {
                    MemoryUtil.memFree(buf);
                }

                // ── Cleanup ──────────────────────────────────────────────────────────
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL30.glBindVertexArray(0);
                GL30.glDeleteVertexArrays(vao);

                // Restore GL state
                if (!wasBlend) RenderSystem.disableBlend();
                if (!wasDepth) RenderSystem.disableDepthTest();
                if (wasCull) RenderSystem.enableCull();

                // Unbind our shader — restore MC's pipeline
                GL20.glUseProgram(0);
            }

            /**
             * Helper: transform a vertex by a part matrix and add it to the vertex list.
             */
            private static void addVertexToList(RenderAPI.Shape.VertexData v, Matrix4f partMatrix,
                    float r, float g, float b, float a,
                    boolean hasColor, boolean hasUv0,
                    List<float[]> out) {
                Vector3f pos = new Vector3f(v.x, v.y, v.z);
                partMatrix.transformPosition(pos);

                int count = 3 + (hasColor ? 4 : 0) + (hasUv0 ? 2 : 0);
                float[] entry = new float[count];
                int idx = 0;
                entry[idx++] = pos.x;
                entry[idx++] = pos.y;
                entry[idx++] = pos.z;
                if (hasColor) {
                    entry[idx++] = r;
                    entry[idx++] = g;
                    entry[idx++] = b;
                    entry[idx++] = a;
                }
                if (hasUv0) {
                    entry[idx++] = v.hasUV ? v.u : 0f;
                    entry[idx++] = v.hasUV ? v.v : 0f;
                }
                out.add(entry);
            }

            /**
             * Render animated with a Shader.
             */
            public void renderAnimatedWithShader(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float scale,
                    Shader shader, int packedLight, int packedOverlay,
                    Animation.Controller controller,
                    float gameTime, float partialTick) {

                if (bufferSource == null) return;
                if (controller == null || !controller.isPlaying()) {
                    renderWithShader(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, packedOverlay);
                    return;
                }

                // ── Apply model-level transform ──
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (scale != 1) poseStack.scale(scale, scale, scale);
                float _origX2 = x, _origY2 = y, _origZ2 = z, _origScale2 = scale;
                x = 0; y = 0; z = 0; scale = 1; // Parts use local coords

                Animation.File animFile = null;
                float animTime = 0;
                try {
                    Identifier animPath = Identifier.parse(controller.getCurrentAnimation());
                    animFile = Animation.File.load(animPath);
                    animTime = controller.getAnimationProgress((long) gameTime, partialTick);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load animation: " + controller.getCurrentAnimation());
                }

                if (animFile == null) {
                    poseStack.popPose();
                    x = _origX2; y = _origY2; z = _origZ2; scale = _origScale2;
                    renderWithShader(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, packedOverlay);
                    return;
                }

                Animation.Controller.AnimationType animType = controller.getAnimationType();

                if (animType == Animation.Controller.AnimationType.PLAY_ONCE
                        && controller.isFinished((long) gameTime, animFile.getLengthTicks())) {
                    controller.stopAnimation();
                    poseStack.popPose();
                    x = _origX2; y = _origY2; z = _origZ2; scale = _origScale2;
                    renderWithShader(poseStack, bufferSource, x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, packedOverlay);
                    return;
                }

                String defaultRT = (shader.getRenderType() != null && !shader.getRenderType().isEmpty())
                        ? shader.getRenderType() : null;
                String defaultTex = shader.getTexture();
                int defaultCol = shader.getColor();

                for (int i = 0; i < parts.size(); i++) {
                    Part part = parts.get(i);

                    // Text parts: render at static position
                    if (part.text != null) {
                        renderTextPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                shader.applyColor(part.color), part.textGlowing,
                                packedLight);
                        continue;
                    }

                    // Java model parts: render at static position (no animation support yet)
                    if (part.javaModelName != null) {
                        renderJavaModelPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                part.texture, part.renderType,
                                packedLight, packedOverlay);
                        continue;
                    }

                    // Item parts: render at static position (no animation support yet)
                    if (part.itemStack != null) {
                        renderItemPart(part, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                packedLight, packedOverlay, shader);
                        continue;
                    }

                    if (part.shape == null || part.shape.isEmpty()) continue;

                    Animation.File.Keyframe kf = animFile.getInterpolatedTransform(i, animTime, animType);
                    if (kf == null) {
                        String rt = defaultRT != null ? defaultRT : part.renderType;
                        String tex = (!part.lockTexture && defaultTex != null) ? defaultTex : part.texture;
                        int col = shader.applyColor(part.color);
                        renderPart(part.shape, poseStack, bufferSource,
                                x + part.x * scale, y + part.y * scale, z + part.z * scale,
                                part.yaw, part.pitch, part.roll,
                                part.xscale * scale, part.yscale * scale, part.zscale * scale,
                                col, tex, rt, packedLight, packedOverlay);
                        continue;
                    }

                    if (!kf.visible) continue;

                    int partColor = (kf.color != -1) ? kf.color : part.color;
                    int col = shader.applyColor(partColor);
                    String partTex = (kf.texture != null && !kf.texture.isEmpty()) ? kf.texture : part.texture;
                    if (!part.lockTexture && defaultTex != null) partTex = defaultTex;
                    String partRT = (kf.renderType != null && !kf.renderType.isEmpty()) ? kf.renderType : part.renderType;
                    if (defaultRT != null) partRT = defaultRT;

                    renderPart(part.shape, poseStack, bufferSource,
                            x + kf.x * scale, y + kf.y * scale, z + kf.z * scale,
                            kf.yaw, kf.pitch, kf.roll,
                            kf.xscale * scale, kf.yscale * scale, kf.zscale * scale,
                            col, partTex, partRT, packedLight, packedOverlay);
                }

                // ── Render temporary shapes (v2) with shader ───────────────────────────
                int tempCount = animFile.getTempShapeCount();
                for (int i = 0; i < tempCount; i++) {
                    Animation.File.TempKeyframe tkf = animFile.getInterpolatedTempShape(i, animTime, animType);
                    if (tkf == null || tkf.vertices.isEmpty()) continue;

                    Animation.File.TempShape ts = animFile.getTempShapes().get(i);

                    int partColorOverride = -1;
                    if (i < parts.size()) {
                        partColorOverride = animFile.getPartColor(i, animTime, animType);
                    }

                    RenderAPI.Shape tempShape = new RenderAPI.Shape();
                    VertexFormat.Mode mode = tkf.mode.equalsIgnoreCase("TRIANGLES")
                            ? VertexFormat.Mode.TRIANGLES : VertexFormat.Mode.QUADS;
                    tempShape.begin(mode, true);

                    for (Animation.File.TempVertex tv : tkf.vertices) {
                        int vColor;
                        if (tv.color != -1) {
                            vColor = tv.color;
                        } else if (partColorOverride != -1) {
                            vColor = partColorOverride;
                        } else {
                            vColor = (ts.color != -1) ? ts.color : -1;
                        }
                        tempShape.addVertexUV(tv.x, tv.y, tv.z, tv.u, tv.v, vColor);
                    }
                    tempShape.end();

                    String shapeTexture = (ts.texture != null && !ts.texture.isEmpty())
                            ? ts.texture : null;
                    if (defaultTex != null) shapeTexture = defaultTex;
                    String shapeRT = (ts.renderType != null && !ts.renderType.isEmpty())
                            ? ts.renderType : "entityCutoutNoCull";
                    if (defaultRT != null) shapeRT = defaultRT;

                    int shapeColor = (ts.color != -1) ? ts.color : -1;
                    shapeColor = shader.applyColor(shapeColor);

                    renderPart(tempShape, poseStack, bufferSource,
                            tkf.x, tkf.y, tkf.z,
                            tkf.yaw, tkf.pitch, tkf.roll,
                            tkf.xscale, tkf.yscale, tkf.zscale,
                            shapeColor, shapeTexture, shapeRT,
                            packedLight, packedOverlay);
                }

                poseStack.popPose();
                x = _origX2; y = _origY2; z = _origZ2; scale = _origScale2;
            }

            /**
             * Render a Java entity model (ModelPart) as a BEWRL part.
             *
             * In the item render context, the PoseStack has Y inverted relative to
             * entity model space (feet at Y=0, grows upward). We fix this with a
             * 180° X rotation — this flips Y→-Y AND Z→-Z while PRESERVING triangle
             * winding order (determinant = +1), so faces stay visible.
             *
             * Do NOT use scale(1,-1,1) — that flips only Y (determinant = -1),
             * reversing winding and making all faces see-through.
             *
             * The 180° X rotation is only applied in the item render context.
             * World and overlay contexts have correct Y orientation.
             */
            private void renderJavaModelPart(Part part, PoseStack poseStack, MultiBufferSource bufferSource,
                    float posX, float posY, float posZ,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    String textureStr, String renderTypeName,
                    int packedLight, int packedOverlay) {

                ModelPart model = JavaModelRenderer.getBakedModel(part.javaModelName);
                if (model == null) return;

                Identifier texture = safeRL(textureStr);
                if (texture == null) texture = Identifier.parse("minecraft:textures/misc/white.png");
                if (renderTypeName == null || renderTypeName.isEmpty()) renderTypeName = "entityCutoutNoCull";

                RenderType rt = JavaModelRenderer.resolveRenderType(renderTypeName, texture);
                VertexConsumer consumer = bufferSource.getBuffer(rt);

                // Detect GUI context for coordinate-space-dependent transforms
                boolean isGui = RenderAPI.getCurrentDisplayContext() == ItemDisplayContext.GUI;

                // Calculate model bounding box center (pixel space -> block space)
                float[] center = JavaModelRenderer.calculateModelCenter(model);
                float cx = center[0] / 16f;
                float cy = center[1] / 16f;
                float cz = center[2] / 16f;

                poseStack.pushPose();

                // 1. User transforms (block space for world, pixel space for GUI)
                poseStack.translate(posX, posY, posZ);
                if (yaw != 0)   poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll != 0)  poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                poseStack.scale(xscale, yscale, zscale);

                // 2. No extra scale needed for any context.
                //    ModelPart.render() handles /16 internally. GUI PoseStack is in the same
                //    normalized block space as world contexts at mixin HEAD.

                // 3. Y 180: entity models face +Z, items face -Z (toward camera)
                poseStack.mulPose(Axis.YP.rotationDegrees(180));

                // 4. Z 180: GUI only -- screen Y goes down, entity model Y goes up
                if (isGui) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180));
                }

                // 5. Center the model (innermost transform -- rotates around center, not feet)
                poseStack.translate(-cx, -cy, -cz);

                // 6. Render -- ModelPart.render() handles /16 internally
                model.render(poseStack, consumer, packedLight, packedOverlay);

                poseStack.popPose();
            }

            private void renderTextPart(Part part, PoseStack poseStack, MultiBufferSource bufferSource,
                    float posX, float posY, float posZ,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color, boolean glowing,
                    int packedLight) {
                if (part.text == null || part.text.isEmpty()) return;
                Font font = Minecraft.getInstance().font;

                poseStack.pushPose();
                poseStack.translate(posX, posY, posZ);
                if (yaw != 0)   poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll != 0)  poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

                float s = (xscale + yscale + zscale) / 3f / 160f;
                poseStack.scale(s, -s, s);

                float textWidth = font.width(part.text);
                float drawX = -textWidth / 2f;
                float drawY = -font.lineHeight / 2f;

                int argbColor = color;
                if ((argbColor >> 24 & 0xFF) == 0) {
                    argbColor = (0xFF << 24) | (argbColor & 0x00FFFFFF);
                }

                org.joml.Matrix4f matrix = poseStack.last().pose();
                Font.DisplayMode displayMode = glowing ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
                font.drawInBatch(part.text, drawX, drawY, argbColor, false,
                    matrix, bufferSource, displayMode, 0, packedLight);

                poseStack.popPose();
            }

            private void renderItemPart(Part part, PoseStack poseStack, MultiBufferSource bufferSource,
                    float posX, float posY, float posZ,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int packedLight, int packedOverlay,
                    Shader shader) {
                if (part.itemStack == null || part.itemStack.isEmpty()) return;

                net.minecraft.world.entity.LivingEntity entity = RenderAPI.getEntity();
                net.minecraft.world.level.Level level = RenderAPI.getWorld();

                // Determine glow: part flag OR shader glowing flag
                boolean isGlowing = part.itemGlowing || (shader != null && shader.isGlowing());
                int light = isGlowing ? net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT : packedLight;

                // Determine shader tint (color + transparency)
                boolean hasTint = false;
                float tintR = 1, tintG = 1, tintB = 1, tintA = 1;
                if (shader != null) {
                    int sc = shader.getColor();
                    if (sc != -1) {
                        tintR = ((sc >> 16) & 0xFF) / 255.0f;
                        tintG = ((sc >> 8) & 0xFF) / 255.0f;
                        tintB = (sc & 0xFF) / 255.0f;
                    }
                    tintA = shader.getTransparency();
                    if (isGlowing && shader.getGlowStrength() > 0) {
                        float boost = 1.0f + shader.getGlowStrength() * 0.5f;
                        tintR = Math.min(1.0f, tintR * boost);
                        tintG = Math.min(1.0f, tintG * boost);
                        tintB = Math.min(1.0f, tintB * boost);
                    }
                    hasTint = (sc != -1 || tintA < 1.0f);
                }

                poseStack.pushPose();
                poseStack.translate(posX, posY, posZ);
                if (yaw != 0)   poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll != 0)  poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

                // Use the current display context (GUI, GROUND, FIRSTPERSON, etc.)
                net.minecraft.world.item.ItemDisplayContext displayCtx = RenderAPI.getCurrentDisplayContext();
                if (displayCtx == null) displayCtx = ItemDisplayContext.NONE;

                // Centered scale: get the display transform's translation and scale
                // around it so the item grows equally from its center.
                if (xscale != 1 || yscale != 1 || zscale != 1) {
                    float cx = 0, cy = 0, cz = 0;
                    try {
                        ResolvedModel bakedModel = Minecraft.getInstance().getItemRenderer()
                                .getModel(part.itemStack, level, entity, 0);
                        ItemTransform itemTransform = bakedModel.wrapped().transforms().getTransform(displayCtx);
                        if (itemTransform != null && itemTransform != ItemTransform.NO_TRANSFORM) {
                            cx = itemTransform.translation.x();
                            cy = itemTransform.translation.y();
                            cz = itemTransform.translation.z();
                        }
                    } catch (Exception ignored) {}
                    poseStack.translate(cx, cy, cz);
                    poseStack.scale(xscale, yscale, zscale);
                    poseStack.translate(-cx, -cy, -cz);
                }

                RenderAPI.setBypassMixin(true);
                try {
                    if (hasTint) {
                        // Use a DEDICATED buffer: render the item, flush immediately
                        // while the tint is active, then restore. This isolates the
                        // tint to only this item part — it won't leak to the player
                        // or other geometry in the shared buffer.
                        float[] savedColor = RenderSystem.getShaderColor();
                        RenderSystem.setShaderColor(tintR, tintG, tintB, tintA);
                        ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                        MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                        try {
                            Minecraft.getInstance().getItemRenderer().renderStatic(
                                entity, part.itemStack, displayCtx, false,
                                poseStack, dedicatedBuffer, level, light, packedOverlay, 0);
                            dedicatedBuffer.endBatch();
                        } finally {
                            builder.close();
                        }
                        RenderSystem.setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
                    } else {
                        Minecraft.getInstance().getItemRenderer().renderStatic(
                            entity, part.itemStack, displayCtx, false,
                            poseStack, bufferSource, level, light, packedOverlay, 0);
                    }
                } finally {
                    RenderAPI.setBypassMixin(false);
                }

                poseStack.popPose();
            }

            /**
             * Render a polyline part as real GL_LINES geometry — the points are placed
             * directly in the model's local space (already-transformed by the enclosing
             * poseStack), with no per-segment rotation.  Because consecutive segments
             * share the exact same vertex position at their shared endpoint, there is
             * no possible visual seam — this is what makes flash-trail lines look like
             * one continuous connected stroke instead of disjointed box facets.
             *
             * Uses vanilla's RenderType.LINES (POSITION_COLOR_NORMAL format, core-profile
             * width expansion handled by the vanilla "rendertype_lines" shader), matching
             * how vanilla debug/hitbox/path lines are drawn.
             */
            private void renderLinePart(Part part, PoseStack poseStack, MultiBufferSource bufferSource) {
                if (bufferSource == null) return;
                float[] pts = part.linePoints;
                int[] cols = part.lineColors;
                int pointCount = pts.length / 3;
                if (pointCount < 2) return;

                VertexConsumer consumer = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.LINES);
                Matrix4f matrix = poseStack.last().pose();

                for (int i = 0; i < pointCount - 1; i++) {
                    float x1 = pts[i * 3];     float y1 = pts[i * 3 + 1];     float z1 = pts[i * 3 + 2];
                    float x2 = pts[(i + 1) * 3]; float y2 = pts[(i + 1) * 3 + 1]; float z2 = pts[(i + 1) * 3 + 2];

                    float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float nx = 0, ny = 1, nz = 0;
                    if (len > 0.0001f) { nx = dx / len; ny = dy / len; nz = dz / len; }

                    int c1 = (cols != null && i < cols.length) ? cols[i] : -1;
                    int c2 = (cols != null && i + 1 < cols.length) ? cols[i + 1] : -1;

                    int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF, a1 = (c1 >> 24) & 0xFF;
                    int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF, a2 = (c2 >> 24) & 0xFF;
                    if (a1 == 0 && c1 == -1) a1 = 255;
                    if (a2 == 0 && c2 == -1) a2 = 255;

                    consumer.addVertex(matrix, x1, y1, z1).setColor(r1, g1, b1, a1).setNormal(nx, ny, nz);
                    consumer.addVertex(matrix, x2, y2, z2).setColor(r2, g2, b2, a2).setNormal(nx, ny, nz);
                }
            }

            private void renderPart(RenderAPI.Shape shape, PoseStack poseStack, MultiBufferSource bufferSource,
                    float posX, float posY, float posZ,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color, String textureStr, String renderTypeName,
                    int packedLight, int packedOverlay) {

                Identifier texture = safeRL(textureStr);
                if (texture == null) texture = Identifier.parse("minecraft:textures/misc/white.png");
                if (renderTypeName == null || renderTypeName.isEmpty()) renderTypeName = "entityCutoutNoCull";

                shape.render(poseStack, bufferSource,
                        posX, posY, posZ,
                        yaw, pitch, roll,
                        xscale, yscale, zscale,
                        color, packedLight, packedOverlay,
                        texture, renderTypeName);
            }

            /**
             * Like renderPart but takes a pre-resolved RenderType instead of a name string.
             * Used for energySwirl with custom speed params, where the RenderType is built
             * with the caller's xSpeed/zSpeed before being passed in.
             */
            private void renderPartWithRenderType(RenderAPI.Shape shape, PoseStack poseStack, MultiBufferSource bufferSource,
                    float posX, float posY, float posZ,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color,
                    net.minecraft.client.renderer.RenderType renderType,
                    int packedLight, int packedOverlay) {
                if (shape == null || shape.isEmpty() || bufferSource == null) return;
                shape.renderWithRenderType(poseStack, bufferSource,
                        posX, posY, posZ,
                        yaw, pitch, roll,
                        xscale, yscale, zscale,
                        color, packedLight, packedOverlay, renderType);
            }

            private static Identifier safeRL(String s) {
                if (s == null || s.isEmpty()) return null;
                try { return Identifier.parse(s); }
                catch (Exception e) { return null; }
            }
    }

    public static class Registry {

        public static final Registry INSTANCE = new Registry();

            private final Map<String, Identifier> registeredPaths = new ConcurrentHashMap<>();
            private final Map<String, Model> loadedModels = new ConcurrentHashMap<>();
            private final Map<String, Animation.Controller> controllers = new ConcurrentHashMap<>();

            /** Guards lazy registration — volatile for thread visibility */
            private volatile boolean registrationFired = false;

            // ── Lazy Registration ───────────────────────────────────────────────────────

            /**
             * Fire the registration event if it hasn't been fired yet.
             * Called lazily from getModel/isRegistered so registration always
             * happens before model access, regardless of mod-bus timing.
             */
            private void ensureRegistered() {
                if (registrationFired) return;
                registrationFired = true;
                try {
                    RegisterEvent event = new RegisterEvent("__MODID__");
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                    LOGGER.info("BEWRL model registration event fired (lazy)");
                } catch (Exception e) {
                    LOGGER.error("Lazy BEWRL registration failed", e);
                }
            }

            // ── Registration ──────────────────────────────────────────────────────────

            void register(String fullId, String path) {
                register(fullId, Identifier.parse(path));
            }

            void register(String fullId, Identifier path) {
                registeredPaths.put(fullId, path);
                loadedModels.remove(fullId);
            }

            // ── Retrieval ─────────────────────────────────────────────────────────────

            public Model getModel(String fullId) {
                // Ensure registration has happened
                ensureRegistered();

                Model cached = loadedModels.get(fullId);
                if (cached != null) return cached;

                Identifier path = registeredPaths.get(fullId);
                if (path == null) {
                    LOGGER.warn("BEWRL model not registered: " + fullId);
                    return null;
                }

                Model model = loadModel(path);
                if (model != null) {
                    loadedModels.put(fullId, model);
                }
                return model;
            }

            public Model getModelCopy(String fullId) {
                Model model = getModel(fullId);
                return model != null ? model.copy() : null;
            }

            public Animation.Controller getController(String fullId) {
                return controllers.computeIfAbsent(fullId, k -> new Animation.Controller(k));
            }

            public boolean isRegistered(String fullId) {
                ensureRegistered();
                return registeredPaths.containsKey(fullId);
            }

            void clear() {
                registeredPaths.clear();
                loadedModels.clear();
                controllers.clear();
                registrationFired = false;
            }

            // ── JSON Model Loading ─────────────────────────────────────────────────────

            /**
             * Load a model JSON file from Minecraft's resource system.
             * Matches the format exported by Builder.
             */
            private Model loadModel(Identifier path) {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    Resource resource = mc.getResourceManager().getResource(path).orElse(null);
                    if (resource == null) {
                        LOGGER.warn("BEWRL model not found: " + path);
                        return null;
                    }

                    JsonObject json;
                    try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                        json = JsonParser.parseReader(reader).getAsJsonObject();
                    }

                    // Build shapes map
                    Map<String, RenderAPI.Shape> shapeMap = new HashMap<>();
                    if (json.has("shapes")) {
                        for (JsonElement se : json.getAsJsonArray("shapes")) {
                            JsonObject so = se.getAsJsonObject();
                            String name = so.get("name").getAsString();
                            RenderAPI.Shape shape = loadShape(so);
                            if (shape != null) shapeMap.put(name, shape);
                        }
                    }

                    // Build model — take first model
                    if (!json.has("models") || json.getAsJsonArray("models").isEmpty()) {
                        LOGGER.warn("No models in model file: " + path);
                        return null;
                    }

                    JsonObject modelObj = json.getAsJsonArray("models").get(0).getAsJsonObject();
                    Model bewrl = new Model();

                    for (JsonElement pe : modelObj.getAsJsonArray("parts")) {
                        JsonObject po = pe.getAsJsonObject();

                        String texture = po.has("texture") ? po.get("texture").getAsString() : "";
                        float x = po.get("x").getAsFloat();
                        float y = po.get("y").getAsFloat();
                        float z = po.get("z").getAsFloat();
                        float yaw = po.has("yaw") ? po.get("yaw").getAsFloat() : 0;
                        float pitch = po.has("pitch") ? po.get("pitch").getAsFloat() : 0;
                        float roll = po.has("roll") ? po.get("roll").getAsFloat() : 0;
                        float xs = po.has("xscale") ? po.get("xscale").getAsFloat() : 1;
                        float ys = po.has("yscale") ? po.get("yscale").getAsFloat() : 1;
                        float zs = po.has("zscale") ? po.get("zscale").getAsFloat() : 1;

                        int color = -1;
                        if (po.has("color")) {
                            color = po.get("color").getAsInt();
                        } else if (po.has("r") || po.has("g") || po.has("b") || po.has("a")) {
                            int r = po.has("r") ? po.get("r").getAsInt() : 255;
                            int g = po.has("g") ? po.get("g").getAsInt() : 255;
                            int b = po.has("b") ? po.get("b").getAsInt() : 255;
                            int a = po.has("a") ? po.get("a").getAsInt() : 255;
                            color = packRGBA(r, g, b, a);
                        }

                        String rt = po.has("render_type") ? po.get("render_type").getAsString()
                                  : po.has("renderType") ? po.get("renderType").getAsString()
                                  : "entityCutoutNoCull";

                        // Check if this is a Java model part or a shape part
                        if (po.has("java_model") || po.has("javaModel")) {
                            String javaModelName = po.has("java_model") ? po.get("java_model").getAsString()
                                                 : po.get("javaModel").getAsString();
                            bewrl.addJavaModelPart(javaModelName, texture, x, y, z, yaw, pitch, roll, xs, ys, zs, color, rt);
                        } else {
                            String shapeName = po.has("shape_name") ? po.get("shape_name").getAsString()
                                           : po.has("shapeName") ? po.get("shapeName").getAsString() : "";
                            RenderAPI.Shape shape = shapeMap.get(shapeName);
                            if (shape == null) continue;
                            bewrl.addPart(shape, texture, x, y, z, yaw, pitch, roll, xs, ys, zs, color, rt);
                        }
                    }

                    LOGGER.info("Loaded BEWRL model from " + path + " with " + bewrl.getParts().size() + " parts");
                    return bewrl.isEmpty() ? null : bewrl;

                } catch (Exception e) {
                    LOGGER.error("Failed to load BEWRL model: " + path, e);
                    return null;
                }
            }

            private static int packRGBA(int r, int g, int b, int a) {
                return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
            }

            private RenderAPI.Shape loadShape(JsonObject so) {
                try {
                    RenderAPI.Shape shape = new RenderAPI.Shape();

                    int faceSize = so.has("face_size") ? so.get("face_size").getAsInt() : 4;
                    com.mojang.blaze3d.vertex.VertexFormat.Mode mode = (faceSize == 3)
                        ? com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES
                        : com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;

                    boolean hasTexture = true;

                    shape.begin(mode, hasTexture);

                    for (JsonElement ve : so.getAsJsonArray("vertices")) {
                        JsonObject vo = ve.getAsJsonObject();
                        float x = vo.get("x").getAsFloat();
                        float y = vo.get("y").getAsFloat();
                        float z = vo.get("z").getAsFloat();

                        int color = -1;
                        if (vo.has("color")) {
                            color = vo.get("color").getAsInt();
                        } else if (vo.has("r") || vo.has("g") || vo.has("b") || vo.has("a")) {
                            int r = vo.has("r") ? vo.get("r").getAsInt() : 255;
                            int g = vo.has("g") ? vo.get("g").getAsInt() : 255;
                            int b = vo.has("b") ? vo.get("b").getAsInt() : 255;
                            int a = vo.has("a") ? vo.get("a").getAsInt() : 255;
                            color = packRGBA(r, g, b, a);
                        }

                        if (hasTexture && vo.has("u")) {
                            float u = vo.get("u").getAsFloat();
                            float v = vo.get("v").getAsFloat();
                            shape.addVertexUV(x, y, z, u, v, color);
                        } else {
                            shape.addVertex(x, y, z, color);
                        }
                    }

                    shape.end();
                    return shape;

                } catch (Exception e) {
                    LOGGER.error("Failed to load shape", e);
                    return null;
                }
            }
    }

    public static class RegisterEvent extends net.neoforged.bus.api.Event {

        private final String modId;

            public RegisterEvent(String modId) {
                this.modId = modId;
            }

            /** The mod id that models will be registered under. */
            public String getModId() {
                return modId;
            }

            /**
             * Register a BEWRL model.
             *
             * @param path     Identifier path to the .fmodel JSON, e.g. "__MODID__:bewrlmodels/sword.json"
             * @param modelId  Short id, e.g. "fire_sword" — stored as "modid:fire_sword"
             */
            public void register(String path, String modelId) {
                String fullId = modId + ":" + modelId;
                Registry.INSTANCE.register(fullId, path);
            }

            /**
             * Register with explicit namespace (for cross-mod registration).
             */
            public void register(String namespace, String path, String modelId) {
                String fullId = namespace + ":" + modelId;
                Registry.INSTANCE.register(fullId, path);
            }
    }


    // ═══════════════════════════════════════════════════════════════
    // Inner class: Animator (Construct animation parameters)
    // ═══════════════════════════════════════════════════════════════
    //
    // A lightweight data holder for construct animation channels.
    // Each field controls one animation effect applied by buildBeam():
    //   pulseSpeed       — oscillates beam length (grow/shrink over time)
    //   flickerIntensity  — random brightness flicker (0=off, 1=extreme; Homelander-style)
    //   colorShiftSpeed  — cycles beam hue over time (rainney laser)
    //   trailIntensity   — fades the far end of the beam (0=solid, 1=fully faded tip)
    //   widthPulseSpeed  — oscillates beam thickness
    //   spinSpeed        — rotates the beam around its longitudinal axis
    //
    // All animations are client-side only and use getRenderTime() — they do
    // NOT sync across clients, which is intentional (each client just plays
    // its own local animation, no network traffic needed).

    public static class Animator {
        public float pulseSpeed = 0;
        public float flickerIntensity = 0;
        public float colorShiftSpeed = 0;
        public float trailIntensity = 0;
        public float widthPulseSpeed = 0;
        public float spinSpeed = 0;

        public Animator() {}

        public Animator(float pulseSpeed, float flickerIntensity, float colorShiftSpeed,
                        float trailIntensity, float widthPulseSpeed, float spinSpeed) {
            this.pulseSpeed = pulseSpeed;
            this.flickerIntensity = flickerIntensity;
            this.colorShiftSpeed = colorShiftSpeed;
            this.trailIntensity = trailIntensity;
            this.widthPulseSpeed = widthPulseSpeed;
            this.spinSpeed = spinSpeed;
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static class RegistrationHandler {

        @SubscribeEvent
            public static void onClientSetup(FMLClientSetupEvent event) {
                event.enqueueWork(() -> {
                    RegisterEvent regEvent = new RegisterEvent("__MODID__");
                    NeoForge.EVENT_BUS.post(regEvent);
                });
            }
    }

}
