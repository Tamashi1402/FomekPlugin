package __RENDERAPI_PACKAGE__;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Stores and retrieves per-ItemStack render override data.
 * Data lives in CUSTOM_DATA["fomek_render"] — auto-synced by vanilla.
 */
public class RenderData {

    private static final String KEY = "fomek_render";

    public static boolean hasOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().contains(KEY);
    }

    public static CompoundTag getRenderData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag root = customData.copyTag();
        return root.contains(KEY) ? root.getCompound(KEY) : null;
    }

    public static void setRenderData(ItemStack stack, CompoundTag data) {
        if (stack == null || stack.isEmpty()) return;
        if (data == null) { removeRenderData(stack); return; }

        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = existing != null ? existing.copyTag() : new CompoundTag();
        root.put(KEY, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void removeRenderData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing == null) return;
        CompoundTag root = existing.copyTag();
        root.remove(KEY);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    public static void enableOverride(ItemStack stack) {
        setRenderData(stack, new CompoundTag());
    }

    public static String getString(ItemStack stack, String key) {
        CompoundTag data = getRenderData(stack);
        return data != null ? data.getString(key) : "";
    }

    public static int getInt(ItemStack stack, String key) {
        CompoundTag data = getRenderData(stack);
        return data != null ? data.getInt(key) : 0;
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        CompoundTag data = getRenderData(stack);
        return data != null && data.getBoolean(key);
    }

    public static void setString(ItemStack stack, String key, String value) {
        CompoundTag data = getRenderData(stack);
        if (data == null) data = new CompoundTag();
        data.putString(key, value);
        setRenderData(stack, data);
    }

    public static void setInt(ItemStack stack, String key, int value) {
        CompoundTag data = getRenderData(stack);
        if (data == null) data = new CompoundTag();
        data.putInt(key, value);
        setRenderData(stack, data);
    }

    public static void setBoolean(ItemStack stack, String key, boolean value) {
        CompoundTag data = getRenderData(stack);
        if (data == null) data = new CompoundTag();
        data.putBoolean(key, value);
        setRenderData(stack, data);
    }
}
