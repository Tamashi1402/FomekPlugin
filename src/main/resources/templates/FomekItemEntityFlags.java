package __RENDERAPI_PACKAGE__;

import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Stores per-entity flags for dropped item rendering overrides.
 * Uses WeakHashMap so entities are GC'd normally when they leave the world.
 */
public class FomekItemEntityFlags {

    // WeakSet pattern
    private static final Set<ItemEntity> CANCEL_SPIN   = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ItemEntity> CANCEL_SHADOW = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ItemEntity> CANCEL_RENDER = Collections.newSetFromMap(new WeakHashMap<>());

    private static final ThreadLocal<Boolean> SPIN_OVERRIDE_ACTIVE = ThreadLocal.withInitial(() -> false);

    // ── cancel spin ──────────────────────────────────────────────────────────

    public static void setCancelSpin(ItemEntity entity, boolean cancel) {
        if (cancel) CANCEL_SPIN.add(entity); else CANCEL_SPIN.remove(entity);
    }

    public static boolean isCancelSpin(ItemEntity entity) {
        return CANCEL_SPIN.contains(entity);
    }

    // ── cancel shadow ────────────────────────────────────────────────────────

    public static void setCancelShadow(ItemEntity entity, boolean cancel) {
        if (cancel) CANCEL_SHADOW.add(entity); else CANCEL_SHADOW.remove(entity);
    }

    public static boolean isCancelShadow(ItemEntity entity) {
        return CANCEL_SHADOW.contains(entity);
    }

    // ── cancel entire render ─────────────────────────────────────────────────

    public static void setCancelRender(ItemEntity entity, boolean cancel) {
        if (cancel) CANCEL_RENDER.add(entity); else CANCEL_RENDER.remove(entity);
    }

    public static boolean isCancelRender(ItemEntity entity) {
        return CANCEL_RENDER.contains(entity);
    }

    // ── thread-local spin override active ────────────────────────────────────

    public static void setSpinOverrideActive(boolean active) {
        SPIN_OVERRIDE_ACTIVE.set(active);
    }

    public static boolean isSpinOverrideActive() {
        return SPIN_OVERRIDE_ACTIVE.get();
    }
}
