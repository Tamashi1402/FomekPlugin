package __RENDERAPI_PACKAGE__;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronized, persistent BEWRL model storage system.
 *
 * Server models are persisted to disk via NeoForge SavedData, surviving
 * world/server restarts. Entity/Item/Block NBT stores only the model ID
 * string; the actual model data lives in the SavedData-backed map and
 * is synced to clients via NeoForge network payloads.
 *
 * Persistence: models are saved to ./<world>/data/__MODID__/bewrl_models.dat
 * on the Overworld (server-wide, not per-dimension).
 */
public class BEWRLStorage {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final ConcurrentHashMap<String, BEWRL.Model> clientStorage = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> clientEntityMap = new ConcurrentHashMap<>();

    private static BEWRLSavedData savedData = null;

    private static final String NBT_PREFIX = "fomek_bewrl_";

    // ═══════════════════════════════════════════════════════════════════════════
    // SavedData implementation — persists serverStorage to disk
    // ═══════════════════════════════════════════════════════════════════════════

    public static class BEWRLSavedData extends SavedData {
        private final ConcurrentHashMap<String, BEWRL.Model> models = new ConcurrentHashMap<>();

        public static BEWRLSavedData create() {
            return new BEWRLSavedData();
        }

        public static BEWRLSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
            BEWRLSavedData data = create();
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                String id = entry.getString("id");
                CompoundTag modelNBT = entry.getCompound("model");
                BEWRL.Model model = modelFromNBT(modelNBT);
                if (model != null && !id.isEmpty()) {
                    data.models.put(id, model);
                }
            }
            LOGGER.info("BEWRL: loaded {} persisted models from disk", data.models.size());
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag entries = new ListTag();
            for (Map.Entry<String, BEWRL.Model> e : models.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", e.getKey());
                entry.put("model", modelToNBT(e.getValue()));
                entries.add(entry);
            }
            tag.put("entries", entries);
            LOGGER.info("BEWRL: saved {} models to disk", models.size());
            return tag;
        }

        public ConcurrentHashMap<String, BEWRL.Model> getModels() {
            return models;
        }

        // Force the SavedData to use our custom file name
        @Override
        public boolean isDirty() {
            return true; // always check — we manage dirty via setDirty()
        }
    }

    /**
     * Called on server start to load (or create) the persistent model storage.
     */
    private static void loadSavedData(MinecraftServer server) {
        savedData = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BEWRLSavedData::create, BEWRLSavedData::load),
                "__MODID___bewrl_models"
        );
        LOGGER.info("BEWRL: storage initialized with {} models", savedData.getModels().size());
    }

    private static ConcurrentHashMap<String, BEWRL.Model> getServerStorage() {
        if (savedData != null) return savedData.getModels();
        // Fallback: if SavedData isn't loaded yet (shouldn't happen during normal play),
        // use a temporary in-memory map
        return fallbackStorage;
    }

    private static final ConcurrentHashMap<String, BEWRL.Model> fallbackStorage = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Model Serialization (Model <-> CompoundTag)
    // ═══════════════════════════════════════════════════════════════════════════

    public static CompoundTag modelToNBT(BEWRL.Model model) {
        if (model == null) return new CompoundTag();
        CompoundTag tag = new CompoundTag();
        ListTag partsList = new ListTag();
        for (BEWRL.Model.Part part : model.getParts()) {
            partsList.add(partToNBT(part));
        }
        tag.put("parts", partsList);
        return tag;
    }

    public static BEWRL.Model modelFromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        BEWRL.Model model = new BEWRL.Model();
        ListTag partsList = tag.getList("parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < partsList.size(); i++) {
            BEWRL.Model.Part part = partFromNBT(partsList.getCompound(i));
            if (part != null) model.getParts().add(part);
        }
        return model;
    }

    private static CompoundTag partToNBT(BEWRL.Model.Part part) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("x", part.x);
        tag.putFloat("y", part.y);
        tag.putFloat("z", part.z);
        tag.putFloat("yaw", part.yaw);
        tag.putFloat("pitch", part.pitch);
        tag.putFloat("roll", part.roll);
        tag.putFloat("xscale", part.xscale);
        tag.putFloat("yscale", part.yscale);
        tag.putFloat("zscale", part.zscale);
        tag.putInt("color", part.color);
        if (part.renderType != null) tag.putString("renderType", part.renderType);
        if (part.texture != null) tag.putString("texture", part.texture);

        if (part.childModel != null) {
            tag.putString("type", "child");
            if (part.childShader != null) tag.put("shader", part.childShader.toNBT());
            tag.put("childModel", modelToNBT(part.childModel));
        } else if (part.javaModelName != null) {
            tag.putString("type", "java");
            tag.putString("javaModelName", part.javaModelName);
        } else if (part.text != null) {
            tag.putString("type", "text");
            tag.putString("text", part.text);
            tag.putBoolean("textGlowing", part.textGlowing);
        } else if (part.shape != null) {
            tag.putString("type", "shape");
            tag.put("shape", shapeToNBT(part.shape));
        } else {
            tag.putString("type", "empty");
        }
        return tag;
    }

    private static BEWRL.Model.Part partFromNBT(CompoundTag tag) {
        String type = tag.getString("type");
        float x = tag.getFloat("x");
        float y = tag.getFloat("y");
        float z = tag.getFloat("z");
        float yaw = tag.getFloat("yaw");
        float pitch = tag.getFloat("pitch");
        float roll = tag.getFloat("roll");
        float xscale = tag.getFloat("xscale");
        float yscale = tag.getFloat("yscale");
        float zscale = tag.getFloat("zscale");
        int color = tag.getInt("color");
        String renderType = tag.contains("renderType") ? tag.getString("renderType") : null;
        String texture = tag.contains("texture") ? tag.getString("texture") : null;

        switch (type) {
            case "child": {
                BEWRL.Model childModel = modelFromNBT(tag.getCompound("childModel"));
                Shader shader = tag.contains("shader") ? Shader.fromNBT(tag.getCompound("shader")) : null;
                return new BEWRL.Model.Part(childModel, shader,
                        x, y, z, yaw, pitch, roll, xscale, yscale, zscale);
            }
            case "java":
                return new BEWRL.Model.Part(tag.getString("javaModelName"), texture,
                        x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color, renderType);
            case "text":
                return new BEWRL.Model.Part(tag.getString("text"), tag.getBoolean("textGlowing"),
                        x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color);
            case "shape":
                return new BEWRL.Model.Part(shapeFromNBT(tag.getCompound("shape")), texture,
                        x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color, renderType);
            default:
                return null;
        }
    }

    private static CompoundTag shapeToNBT(RenderAPI.Shape shape) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", shape.getMode().name());
        tag.putBoolean("hasTexture", shape.hasTexture());
        tag.putBoolean("ended", shape.hasEnded());

        ListTag vertList = new ListTag();
        for (RenderAPI.Shape.VertexData v : shape.getVertices()) {
            CompoundTag vt = new CompoundTag();
            vt.putFloat("x", v.x);
            vt.putFloat("y", v.y);
            vt.putFloat("z", v.z);
            vt.putFloat("u", v.u);
            vt.putFloat("v", v.v);
            vt.putInt("color", v.color);
            vt.putBoolean("hasUV", v.hasUV);
            vertList.add(vt);
        }
        tag.put("vertices", vertList);
        return tag;
    }

    private static RenderAPI.Shape shapeFromNBT(CompoundTag tag) {
        RenderAPI.Shape shape = new RenderAPI.Shape();
        String modeName = tag.getString("mode");
        boolean hasTexture = tag.getBoolean("hasTexture");
        boolean ended = tag.getBoolean("ended");

        try {
            com.mojang.blaze3d.vertex.VertexFormat.Mode mode =
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.valueOf(modeName);
            shape.begin(mode, hasTexture);
        } catch (Exception e) {
            shape.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, hasTexture);
        }

        ListTag vertList = tag.getList("vertices", Tag.TAG_COMPOUND);
        for (int i = 0; i < vertList.size(); i++) {
            CompoundTag vt = vertList.getCompound(i);
            if (vt.getBoolean("hasUV")) {
                shape.addVertexUV(vt.getFloat("x"), vt.getFloat("y"), vt.getFloat("z"),
                        vt.getFloat("u"), vt.getFloat("v"), vt.getInt("color"));
            } else {
                shape.addVertex(vt.getFloat("x"), vt.getFloat("y"), vt.getFloat("z"), vt.getInt("color"));
            }
        }
        if (ended) shape.end();
        return shape;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Storage API
    // ═══════════════════════════════════════════════════════════════════════════

    public static String storeModel(BEWRL.Model model) {
        if (model == null) return "";
        String id = UUID.randomUUID().toString();
        getServerStorage().put(id, model);
        if (savedData != null) savedData.setDirty();
        PacketDistributor.sendToAllPlayers(new BEWRLModelPayload(id, modelToNBT(model), false));
        return id;
    }

    public static void removeModel(String id) {
        if (id == null || id.isEmpty()) return;
        getServerStorage().remove(id);
        if (savedData != null) savedData.setDirty();
        PacketDistributor.sendToAllPlayers(new BEWRLModelPayload(id, null, true));
    }

    public static BEWRL.Model getModel(String id) {
        if (id == null || id.isEmpty()) return null;
        if (FMLEnvironment.dist.isClient()) return clientStorage.get(id);
        return getServerStorage().get(id);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Entity BEWRL API
    // ═══════════════════════════════════════════════════════════════════════════

    public static void setEntityBEWRL(Entity entity, String tagName, BEWRL.Model model) {
        if (entity == null || tagName == null || tagName.isEmpty()) return;
        String key = NBT_PREFIX + tagName;
        String oldId = entity.getPersistentData().getString(key);
        if (!oldId.isEmpty()) removeModel(oldId);

        if (model == null || model.isEmpty()) {
            entity.getPersistentData().remove(key);
            PacketDistributor.sendToAllPlayers(new BEWRLEntityMapPayload(entity.getUUID(), tagName, "", true));
            return;
        }

        String modelId = storeModel(model);
        entity.getPersistentData().putString(key, modelId);
        PacketDistributor.sendToAllPlayers(new BEWRLEntityMapPayload(entity.getUUID(), tagName, modelId, false));
    }

    public static BEWRL.Model getEntityBEWRL(Entity entity, String tagName) {
        if (entity == null || tagName == null || tagName.isEmpty()) return null;
        String key = NBT_PREFIX + tagName;

        if (FMLEnvironment.dist.isClient()) {
            String mapKey = entity.getUUID() + ":" + tagName;
            String modelId = clientEntityMap.get(mapKey);
            if (modelId != null && !modelId.isEmpty()) return clientStorage.get(modelId);
            String pid = entity.getPersistentData().getString(key);
            if (!pid.isEmpty()) return clientStorage.get(pid);
            return null;
        }
        String modelId = entity.getPersistentData().getString(key);
        return modelId.isEmpty() ? null : getServerStorage().get(modelId);
    }

    public static void removeEntityBEWRL(Entity entity, String tagName) {
        if (entity == null || tagName == null || tagName.isEmpty()) return;
        String key = NBT_PREFIX + tagName;
        String modelId = entity.getPersistentData().getString(key);
        if (!modelId.isEmpty()) removeModel(modelId);
        entity.getPersistentData().remove(key);
        PacketDistributor.sendToAllPlayers(new BEWRLEntityMapPayload(entity.getUUID(), tagName, "", true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ItemStack BEWRL API
    // ═══════════════════════════════════════════════════════════════════════════

    public static void setItemBEWRL(ItemStack stack, String tagName, BEWRL.Model model) {
        if (stack == null || stack.isEmpty() || tagName == null || tagName.isEmpty()) return;
        String key = NBT_PREFIX + tagName;

        CustomData oldData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag oldTag = oldData.copyTag();
        if (oldTag.contains(key)) {
            String oldId = oldTag.getString(key);
            if (!oldId.isEmpty()) removeModel(oldId);
        }

        if (model == null || model.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(key));
            return;
        }

        String modelId = storeModel(model);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(key, modelId));
    }

    public static BEWRL.Model getItemBEWRL(ItemStack stack, String tagName) {
        if (stack == null || stack.isEmpty() || tagName == null || tagName.isEmpty()) return null;
        String key = NBT_PREFIX + tagName;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(key)) return null;
        String modelId = tag.getString(key);
        return modelId.isEmpty() ? null : getModel(modelId);
    }

    public static void removeItemBEWRL(ItemStack stack, String tagName) {
        if (stack == null || stack.isEmpty() || tagName == null || tagName.isEmpty()) return;
        String key = NBT_PREFIX + tagName;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (tag.contains(key)) {
            String modelId = tag.getString(key);
            if (!modelId.isEmpty()) removeModel(modelId);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(key));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Block Entity BEWRL API
    // ═══════════════════════════════════════════════════════════════════════════

    public static void setBlockBEWRL(net.minecraft.world.level.LevelAccessor world, BlockPos pos,
                                      String tagName, BEWRL.Model model) {
        if (world == null || pos == null || tagName == null || tagName.isEmpty()) return;
        if (!(world instanceof net.minecraft.world.level.Level level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        String key = NBT_PREFIX + tagName;
        CompoundTag existingData = be.getPersistentData();
        if (existingData.contains(key)) {
            String oldId = existingData.getString(key);
            if (!oldId.isEmpty()) removeModel(oldId);
        }

        if (model == null || model.isEmpty()) {
            be.getPersistentData().remove(key);
            be.setChanged();
            return;
        }

        String modelId = storeModel(model);
        be.getPersistentData().putString(key, modelId);
        be.setChanged();
    }

    public static BEWRL.Model getBlockBEWRL(net.minecraft.world.level.LevelAccessor world, BlockPos pos,
                                                  String tagName) {
        if (world == null || pos == null || tagName == null || tagName.isEmpty()) return null;
        if (!(world instanceof net.minecraft.world.level.Level level)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        String key = NBT_PREFIX + tagName;
        CompoundTag data = be.getPersistentData();
        if (!data.contains(key)) return null;
        String modelId = data.getString(key);
        return modelId.isEmpty() ? null : getModel(modelId);
    }

    public static void removeBlockBEWRL(net.minecraft.world.level.LevelAccessor world, BlockPos pos,
                                        String tagName) {
        if (world == null || pos == null || tagName == null || tagName.isEmpty()) return;
        if (!(world instanceof net.minecraft.world.level.Level level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        String key = NBT_PREFIX + tagName;
        CompoundTag data = be.getPersistentData();
        if (data.contains(key)) {
            String modelId = data.getString(key);
            if (!modelId.isEmpty()) removeModel(modelId);
        }
        be.getPersistentData().remove(key);
        be.setChanged();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Client-side sync handlers
    // ═══════════════════════════════════════════════════════════════════════════

    private static void handleModelSync(String modelId, CompoundTag modelNBT, boolean isRemoval) {
        if (isRemoval) {
            clientStorage.remove(modelId);
        } else if (modelNBT != null) {
            BEWRL.Model model = modelFromNBT(modelNBT);
            if (model != null) clientStorage.put(modelId, model);
        }
    }

    private static void handleEntityMapSync(UUID entityUuid, String tagName,
                                             String modelId, boolean isRemoval) {
        String mapKey = entityUuid + ":" + tagName;
        if (isRemoval || modelId == null || modelId.isEmpty()) {
            clientEntityMap.remove(mapKey);
        } else {
            clientEntityMap.put(mapKey, modelId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Server lifecycle: load SavedData on start, re-sync players on login
    // ═══════════════════════════════════════════════════════════════════════════

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            loadSavedData(event.getServer());
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                for (Map.Entry<String, BEWRL.Model> entry : getServerStorage().entrySet()) {
                    PacketDistributor.sendToPlayer(sp,
                            new BEWRLModelPayload(entry.getKey(), modelToNBT(entry.getValue()), false));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Network Payloads
    // ═══════════════════════════════════════════════════════════════════════════

    public record BEWRLModelPayload(String modelId, CompoundTag modelNBT, boolean isRemoval)
            implements CustomPacketPayload {

        public static final Type<BEWRLModelPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("__MODID__", "bewrl_model"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BEWRLModelPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUtf(payload.modelId);
                            buf.writeBoolean(payload.isRemoval);
                            if (!payload.isRemoval && payload.modelNBT != null) {
                                buf.writeBoolean(true);
                                buf.writeNbt(payload.modelNBT);
                            } else {
                                buf.writeBoolean(false);
                            }
                        },
                        (buf) -> {
                            String id = buf.readUtf();
                            boolean removal = buf.readBoolean();
                            CompoundTag nbt = null;
                            if (buf.readBoolean()) nbt = buf.readNbt();
                            return new BEWRLModelPayload(id, nbt, removal);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BEWRLEntityMapPayload(UUID entityUuid, String tagName,
                                         String modelId, boolean isRemoval)
            implements CustomPacketPayload {

        public static final Type<BEWRLEntityMapPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("__MODID__", "bewrl_entity_map"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BEWRLEntityMapPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUUID(payload.entityUuid);
                            buf.writeUtf(payload.tagName);
                            buf.writeBoolean(payload.isRemoval);
                            if (!payload.isRemoval && payload.modelId != null) {
                                buf.writeBoolean(true);
                                buf.writeUtf(payload.modelId);
                            } else {
                                buf.writeBoolean(false);
                            }
                        },
                        (buf) -> {
                            UUID uuid = buf.readUUID();
                            String tag = buf.readUtf();
                            boolean removal = buf.readBoolean();
                            String modelId = null;
                            if (buf.readBoolean()) modelId = buf.readUtf();
                            return new BEWRLEntityMapPayload(uuid, tag, modelId, removal);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Network Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1")
                .playBidirectional(BEWRLModelPayload.TYPE, BEWRLModelPayload.STREAM_CODEC,
                        (payload, context) -> {
                            context.enqueueWork(() -> {
                                if (FMLEnvironment.dist.isClient()) {
                                    handleModelSync(payload.modelId(), payload.modelNBT(), payload.isRemoval());
                                }
                            });
                        })
                .playBidirectional(BEWRLEntityMapPayload.TYPE, BEWRLEntityMapPayload.STREAM_CODEC,
                        (payload, context) -> {
                            context.enqueueWork(() -> {
                                if (FMLEnvironment.dist.isClient()) {
                                    handleEntityMapSync(payload.entityUuid(), payload.tagName(),
                                            payload.modelId(), payload.isRemoval());
                                }
                            });
                        });
        }
    }
}
