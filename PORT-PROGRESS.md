# Render-runtime port: MCreator 2026.1 → 2026.2 / NeoForge 26.1.2

## Status
- Workspace-build harness (pluginWorkspaceTest) WORKS: creates real neoforge-26.1.2
  workspace, injects fomek render runtime, runs full Gradle build.
  Log: repo/build/workspace-test-build.log; run:
  `env JAVA_HOME=mcreator-src/jdk/jbr25_linux_64 ./gradlew pluginWorkspaceTest --no-daemon`
  (must run with workingDir=mcreator_path so ./plugins/ built-ins are found;
  FOMEK_TEST_DIR env passes repo dir for outputs; test resources on classpath via
  mcreator_src/src/test/resources — has /empty.nbt the workspace filler needs).
- Pass 1 (mechanical renames) applied to 12 template files (RenderAPI + Fomek twin,
  RenderEvent, BEWRL, Animation, Shader, BEWRLStorage ×2 each).
- Errors before pass 1: ~300 javac errors (RenderAPI 74, BEWRL 18, RenderEvent 8,
  Shader 10, Animation 6 + 2× "wrong number of type arguments; required 2").

## Verified MC 26.1 → 1.21.1 symbol map (from minecraft-patched-26.1.2.95-sources.jar)
| old (1.21.1) | new (26.1) |
|---|---|
| net.minecraft.resources.ResourceLocation | net.minecraft.resources.Identifier (fromNamespaceAndPath/parse/tryParse/withDefaultNamespace; NO public ctor) |
| net.minecraft.client.gui.GuiGraphics | net.minecraft.client.gui.GuiGraphicsExtractor (has all drawing: fill, fillGradient, text(=drawString), centeredText, textWithWordWrap, blit, blitSprite, item(=renderItem), itemDecorations, setTooltipForNextFrame, entity, pose()) |
| blit(RenderType, RL, x,y,u,v,w,h,tw,th) | blit(RenderPipeline, Identifier, x,y,u,v,w,h,tw,th[,color]) — pipeline usually RenderPipelines.GUI_TEXTURED |
| gui.pose() : PoseStack | gui.pose() : org.joml.Matrix3x2fStack (2D! pushMatrix/popMatrix/translate(x,y)/scale(x,y)/rotate(rad); NO z. Depth = gui.nextStratum()) |
| net.minecraft.client.renderer.RenderType | net.minecraft.client.renderer.rendertype.RenderType |
| RenderType.entityCutout/static factories | RenderTypes.entityCutout etc. (same package). MISSING: entityCutoutNoCull→entityCutout, entityGlintDirect→entityGlint, dragonExplosionAlpha→dragonRays (VISUAL VERIFY) |
| RenderStateShard | GONE → RenderSetup.builder(RenderPipeline) + RenderType.create(name, setup) |
| net.minecraft.client.renderer.LightTexture.FULL_BRIGHT | net.minecraft.util.LightCoordsUtil.FULL_BRIGHT (=15728880) |
| net.minecraft.client.resources.model.BakedModel | net.minecraft.client.resources.model.ResolvedModel (interface; transforms via model.wrapped().transforms() → ItemTransforms record, may be null) |
| BakedModel.getTransforms() | resolvedModel.wrapped().transforms() (nullable) |
| net.minecraft.client.renderer.block.model.BakedQuad | net.minecraft.client.resources.model.geometry.BakedQuad — now a RECORD (Vector3fc positions, packed UV longs, MaterialInfo) — construction/consumption API fully changed |
| net.minecraft.client.renderer.block.model.ItemTransform | net.minecraft.client.resources.model.cuboid.ItemTransform (record: rotation/translation/scale :Vector3fc) |
| com.mojang.blaze3d.platform.GlStateManager | com.mojang.blaze3d.opengl.GlStateManager; SourceFactor/DestFactor enums GONE |
| RenderSystem.setShaderTexture/setShaderColor/blendFunc(Separate)/defaultBlendFunc/enableBlend/depth | GONE from RenderSystem — blend+color state now baked per-RenderPipeline; custom blend = build own RenderPipeline (see vanilla RenderPipelines class pattern) |
| BufferUploader | GONE (blaze3d GpuDevice/MeshRenderer API) |
| EntityRenderer<T> | EntityRenderer<T, S extends EntityRenderState> (2 type args; extractRenderState + submit) |
| ItemRenderer.getModel(stack,lvl,ent,seed) | UNRESOLVED — no client ItemRenderer.getModel found; render-state pipeline (TrackingItemStackRenderState / GuiItemRenderState) — investigate Minecraft.getItemRenderer replacement |
| neoforge RenderGuiEvent.getGuiGraphics() | same name, returns GuiGraphicsExtractor (OK) |
| RenderLevelStageEvent | now (LevelRenderer, LevelRenderState, PoseStack, Matrix4fc, sections) — pose/modelview accessors still there |
| RegisterClientReloadListenersEvent | NOT FOUND in neoforge universal jar — check client jar / rename |

## Known deep-work items after pass 1
1. RenderAPI blend-mode subsystem (enableBlending / flushBufferWithBlend /
   resolveRenderType custom blend path, ~lines 760-840 + 5780+): immediate-mode
   blend hacks are impossible now. Port = custom RenderPipelines per BlendMode
   (copy RenderPipelines.java builder pattern), RenderSetup.builder per texture.
2. Item display-transform access (getItemDisplayYaw/Pitch/Roll + applyTransform):
   needs new model-resolution path (item 1 in table above).
3. Shape custom RenderType / swirl animation (energySwirl had UV-offset params in
   old API — check RenderTypes.energySwirl signature: (Identifier, Identifier, x, z)).
4. BEWRL: BakedQuad record port (quad consumers in reconstructResolvedModel),
   ItemTransform record fields (was public Vector3f fields, now record accessors —
   .rotation.y() still works).
5. Shader.java: RenderStateShard import was removed; class still references
   shard API for custom types — needs RenderSetup port; check GL20/LWJGL usage compiles headless.
6. "wrong number of type arguments" — EntityRenderer<?> → EntityRenderer<?,?> at RenderAPI.java:2788.

## Harness notes
- deobf sources: repo/build/workspace-test/build/moddev/artifacts/minecraft-patched-26.1.2.95-sources.jar
- neoforge universal jar: /root/.mcreator/gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.95/b2c6a32bb07fe4e8f919bfb76cf1645be0a17b48/neoforge-26.1.2.95-universal.jar
- MCreator 2026.2 generator 26.1 templates are the idiomatic reference:
  mcreator-src/plugins/generator-26.1.x/neoforge-26.1.2/templates (blockentity_renderer.java.ftl = new render-state architecture)

## Chunk 3 findings (GUI item overlay rewrite — next up)
Old path: `Minecraft.getItemRenderer().renderStatic(entity, stack, GUI, false, pose, gui.bufferSource(), level, light, overlay, seed)` + pose.pushPose/translate/mulPose/scale — ALL GONE.

New vanilla pattern (from GuiGraphicsExtractor.item):
```java
TrackingItemStackRenderState state = new TrackingItemStackRenderState();
Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, level, owner, seed);
gui.submitGuiElementRenderState(new GuiItemRenderState(new Matrix3x2f(gui.pose()), state, x, y, null)); // guiRenderState.addItem() is the internal call
```
- gui.pose() is 2D Matrix3x2fStack: pushMatrix/popMatrix/translate(x,y)/scale(x,y)/rotate(rad). No z. Stratum = gui.nextStratum().
- 3D item angles: ItemStackRenderState.LayerRenderState.setLocalTransform(Matrix4fc) per layer
  (layers are set by updateForTopItem; iterate state layers) — this is how to inject
  yaw/pitch/roll/scale into the baked model. Also setItemTransform(ItemTransform).
- Direct 3D submit: state.submit(PoseStack, SubmitNodeCollector, lightCoords, overlay, outlineColor)
  — lightCoords 15728880 = FULL_BRIGHT (see OversizedItemRenderer.renderToTexture:
  poseStack.scale(1,-1,-1) then submit). SubmitNodeStorage = concrete collector.
- ItemTransform record: rotation()/translation()/scale()/rightRotation() accessors (fields private).
- VertexConsumer now requires setLineWidth(float) override (RenderAPI anon class ~2676).
- Model.setupAnim signature changed (RenderAPI 1885-1889).
- EntityRenderer now 2 generics: EntityRenderer<?,?>.
- PageFlip.java: 4 errors — BookModel/PageFlip API moved (check BookModel in 26.1.2).
- @EventBusSubscriber: bus attr GONE, only value (Dist[]) + modid remain.
- RegisterClientReloadListenersEvent → AddClientReloadListenersEvent;
  event.addListener(Identifier id, PreparableReloadListener) (was registerReloadListener).

## Chunk 4-6 progress (2026-10-07 evening) — ERROR COUNT: ~200 (was 300)

### Done & verified
- GUI overlay redraw (Chunk 4): GuiGraphicsExtractor paths — submitOverlayGuiItem via
  getItemModelResolver+GuiItemRenderState, submitOverlayEntity via createRenderState,
  2D Matrix3x2fStack helpers, stratum depth. Old ItemRenderer.renderStatic GONE.
- RenderEvent world stage: RenderLevelStageEvent.AfterTranslucentParticles sub-event,
  camera via gameRenderer.getMainCamera(), DeltaTracker via mc.getDeltaTracker().
- FomekFlicker model posing via createRenderState + setupAnim(state).
- Shader.java: apply/restore/applyUniforms → 26.1 no-ops (global shader color +
  ShaderInstance uniforms GONE; raw GLSL path manages own uniforms). Dead
  RenderStateShard reflection helpers removed.
- Projection matrix: RenderSystem.getProjectionMatrix() GONE — matrix only lives in a
  GPU UBO (GpuBufferSlice). Shader.Manager.readProjectionMatrix() reads it back via
  GlBuffer.handle reflection + GL45 glGetNamedBufferSubData (std140 col-major mat4).
- BEWRL item parts: resolve via TrackingItemStackRenderState + drawItemQuads
  (RenderAPI helper emits baked quads via putBakedQuad + QuadInstance);
  tint baked per-vertex via multiplyArgb (no setShaderColor anymore).
- Raw-GL blend/depth/cull toggles in BEWRL → plain GL11 calls (RenderSystem toggles GONE).
- NBT Optional getters swept: getStringOr/getIntOr/getFloatOr/getLongOr/getDoubleOr/
  getByteOr/getBooleanOr/getCompoundOrEmpty/getListOrEmpty, ListTag.getCompoundOrEmpty(i),
  receivers any local (Animation, Shader, BEWRLStorage ×9 receivers, RenderData).
- Package renames: rendertype.RenderType/RenderTypes, util.LightCoordsUtil.
- EntityRenderer<?,?> generics, VertexConsumer.setLineWidth override, Model.setupAnim(state),
  EventBusSubscriber bus attr, AddClientReloadListenersEvent.addListener(Identifier,...).

### Open errors (~200, mostly cascades)
- BEWRLStorage: 96 — mostly NBT getter sweep fallout (now fixed, unverified) +
  4× FMLEnvironment.dist (NeoForge 26.1: check FMLEnvironment API in universal jar).
- RenderAPI: 46 — ghost-collector getTextureLocation + blend subsystem (deep item 1).
- Shader: 8 — glGetNamedBufferSubData signature (LWJGL wants (int,long,ByteBuffer) or
  (int,long,long,ByteBuffer)?), JOML Matrix4f has NO float[] ctor → new Matrix4f().set(m),
  CompoundTag.getAllKeys() → check replacement (keySet()?).
- BEWRL: 6 — RenderTypes.energySwirl signature, RenderType.LINES, GL14 glBlendFuncSeparate.
- RenderData: 8 (NBT conditionals, likely fixed by sweep), Animation: 4 (fixed),
  JavaModelRenderer: 2 — dragonExplosionAlpha → RenderTypes.dragonRays? (VISUAL VERIFY).
- PageFlip: 4 — BookModel API in 26.1 (check sources jar).

### Deep-work queue (unchanged)
1. RenderAPI blend-mode subsystem → custom RenderPipelines per BlendMode.
2. Shape custom RenderType/swirl — RenderTypes.energySwirl(Identifier, Identifier, x, z)?
3. BEWRL BakedQuad record port in reconstructResolvedModel.
