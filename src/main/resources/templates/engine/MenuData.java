package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * MenuData — persistent per-menu variable storage backed by NBT.
 *
 * Each MenuData wraps a {@link CompoundTag} and provides typed get/set
 * operations by string key.  Data persists in a static map across menu
 * open/close cycles.  Use {@link #save(String)} to persist to storage and
 * {@link #load(String)} to retrieve.
 *
 * <h3>Supported types</h3>
 * <ul>
 *   <li>double   — numbers</li>
 *   <li>boolean  — true/false</li>
 *   <li>String  — text</li>
 *   <li>ItemStack — items</li>
 *   <li>BlockState — block states</li>
 *   <li>CompoundTag — nested data maps (maps of maps)</li>
 *   <li>ListTag — data lists</li>
 * </ul>
 */
public class MenuData {

    private final CompoundTag tag = new CompoundTag();

    // ── Static storage (persists across menu open/close) ─────────────────────

    private static final Map<String, MenuData> storage = new HashMap<>();

    public static MenuData load(String id) {
        MenuData existing = storage.get(id);
        if (existing != null) return existing;
        return new MenuData();
    }

    public static void save(String id, MenuData data) {
        storage.put(id, data);
    }

    public static void clear(String id) {
        storage.remove(id);
    }

    public static boolean exists(String id) {
        return storage.containsKey(id);
    }

    public static void clearAll() {
        storage.clear();
    }

    // ── Registry access helper ──────────────────────────────────────────────────

    private static HolderLookup.Provider getProvider() {
        var mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static HolderGetter<Block> getBlockGetter() {
        HolderLookup.Provider provider = getProvider();
        if (provider != null) return (HolderGetter<Block>) provider.lookup(Registries.BLOCK).orElse(null);
        return null;
    }

    // ── Typed getters ──────────────────────────────────────────────────────

    public double getDouble(String key) {
        return tag.getDouble(key);
    }

    public boolean getBoolean(String key) {
        return tag.getBoolean(key);
    }

    public String getString(String key) {
        return tag.getString(key);
    }

    public ItemStack getItem(String key) {
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            HolderLookup.Provider provider = getProvider();
            if (provider != null) {
                return ItemStack.parseOptional(provider, tag.getCompound(key));
            }
        }
        return ItemStack.EMPTY;
    }

    public BlockState getBlockState(String key) {
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            HolderGetter<Block> getter = getBlockGetter();
            if (getter != null) {
                return NbtUtils.readBlockState(getter, tag.getCompound(key));
            }
        }
        return null;
    }

    public CompoundTag getCompound(String key) {
        return tag.getCompound(key);
    }

    public ListTag getList(String key) {
        if (tag.contains(key, Tag.TAG_LIST)) {
            return tag.getList(key, Tag.TAG_COMPOUND);
        }
        return new ListTag();
    }

    // ── Typed setters ──────────────────────────────────────────────────────

    public void setDouble(String key, double value) {
        tag.putDouble(key, value);
    }

    public void setBoolean(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    public void setString(String key, String value) {
        tag.putString(key, value);
    }

    public void setItem(String key, ItemStack item) {
        HolderLookup.Provider provider = getProvider();
        if (provider != null && item != null) {
            CompoundTag itemTag = new CompoundTag();
            item.save(provider, itemTag);
            tag.put(key, itemTag);
        }
    }

    public void setBlockState(String key, BlockState state) {
        if (state != null) {
            tag.put(key, NbtUtils.writeBlockState(state));
        }
    }

    public void setCompound(String key, CompoundTag compound) {
        tag.put(key, compound);
    }

    public void setList(String key, ListTag list) {
        tag.put(key, list);
    }

    public void remove(String key) {
        tag.remove(key);
    }

    public boolean contains(String key) {
        return tag.contains(key);
    }

    public CompoundTag getTag() {
        return tag;
    }

    public static MenuData fromTag(CompoundTag source) {
        MenuData data = new MenuData();
        if (source != null) {
            for (String key : source.getAllKeys()) {
                data.tag.put(key, source.get(key));
            }
        }
        return data;
    }
}
