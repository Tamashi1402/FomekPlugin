package __RENDERAPI_PACKAGE__;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Optional bridge to the FomekCore mod (net.tamashi.fomekcore).
 *
 * FomekPlugin does NOT need FomekCore to compile or run. All calls into
 * FomekCore's TickRateManager go through reflection here:
 *
 *  - When the "Fomek Core" plugin is installed AND its API ("Fomek: Core")
 *    is enabled in the workspace external APIs, the fomek-core jar is on the
 *    classpath, reflection binds once and slowmo rendering becomes active.
 *  - Otherwise (API unchecked or FomekCore not installed) every method
 *    degrades gracefully and callers fall back to vanilla behaviour.
 */
public final class FomekCoreCompat {

    private static final String MGR = "net.tamashi.fomekcore.api.TickRateManager";

    /** Slowmo-adjusted delta tracker info for one entity. */
    public static final class DeltaInfo {
        public final float partialTick;
        public final float deltaTicks;

        private DeltaInfo(float partialTick, float deltaTicks) {
            this.partialTick = partialTick;
            this.deltaTicks = deltaTicks;
        }
    }

    /** Tick state snapshot of one entity. */
    public static final class TickState {
        public final float rate;
        public final boolean frozen;

        private TickState(float rate, boolean frozen) {
            this.rate = rate;
            this.frozen = frozen;
        }
    }

    private static final boolean AVAILABLE;
    private static Method serverHasMod;
    private static Method getEntityDeltaTrackerInfo;
    private static Method getEntityEffectiveRateM;
    private static Method getEntityTickStateOrNull;
    private static Method deltaPartialTick;
    private static Method deltaDeltaTicks;
    private static Method stateRate;
    private static Method stateFrozen;

    static {
        boolean ok = false;
        try {
            Class<?> manager = Class.forName(MGR, false,
                    FomekCoreCompat.class.getClassLoader());
            Class<?> clientManager = Class.forName(MGR + "$TickRateClientManager", false,
                    FomekCoreCompat.class.getClassLoader());
            serverHasMod = clientManager.getMethod("serverHasMod");
            getEntityDeltaTrackerInfo = clientManager.getMethod("getEntityDeltaTrackerInfo", Entity.class);
            getEntityEffectiveRateM = manager.getMethod("getEntityEffectiveRate", Entity.class);
            getEntityTickStateOrNull = manager.getMethod("getEntityTickStateOrNull", Entity.class);
            deltaPartialTick = getEntityDeltaTrackerInfo.getReturnType().getMethod("partialTick");
            deltaDeltaTicks = getEntityDeltaTrackerInfo.getReturnType().getMethod("deltaTicks");
            stateRate = getEntityTickStateOrNull.getReturnType().getMethod("rate");
            stateFrozen = getEntityTickStateOrNull.getReturnType().getMethod("frozen");
            ok = true;
        } catch (Throwable t) {
            // FomekCore not installed or its API is not enabled in this workspace
        }
        AVAILABLE = ok;
    }

    private FomekCoreCompat() {}

    /** True only when FomekCore is on the classpath AND the server has the mod. */
    public static boolean isSlowmoActive() {
        if (!AVAILABLE) return false;
        try {
            return (Boolean) serverHasMod.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Entity-specific delta tracker info (perception-aware, resolves
     * entity -> chunk -> server), or null when FomekCore is absent/inactive.
     */
    public static DeltaInfo getEntityDeltaInfo(Entity entity) {
        if (entity == null || !isSlowmoActive()) return null;
        try {
            Object info = getEntityDeltaTrackerInfo.invoke(null, entity);
            if (info == null) return null;
            return new DeltaInfo((Float) deltaPartialTick.invoke(info),
                    (Float) deltaDeltaTicks.invoke(info));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Chunk-resolved effective tick rate; 20.0 (vanilla) when unavailable. */
    public static float getEntityEffectiveRate(Entity entity) {
        if (entity == null || !isSlowmoActive()) return 20.0f;
        try {
            return (Float) getEntityEffectiveRateM.invoke(null, entity);
        } catch (Throwable t) {
            return 20.0f;
        }
    }

    /** Entity tick state, or null when FomekCore is absent or has no state. */
    public static TickState getEntityTickState(Entity entity) {
        if (entity == null || !isSlowmoActive()) return null;
        try {
            Object state = getEntityTickStateOrNull.invoke(null, entity);
            if (state == null) return null;
            return new TickState((Float) stateRate.invoke(state),
                    (Boolean) stateFrozen.invoke(state));
        } catch (Throwable t) {
            return null;
        }
    }
}
