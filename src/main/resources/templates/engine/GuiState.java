package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GuiState — Synchronized per-player mouse and interaction state.
 *
 * Tracks mouse position, click state (left/right), and drag data.
 * Clicks stay "true" until explicitly cleared (by the user's procedure
 * or when the screen closes).
 *
 * Client-side state is updated by MenuEventHandler via NeoForge ScreenEvent.
 * Server-side state is updated via GuiStatePayload network packet.
 *
 * The RenderEvent inner class is fired on the NeoForge event bus
 * during ScreenEvent.Render when a virtual GUI is open.
 */
public class GuiState {

    // ── CLIENT-SIDE STATE ──────────────────────────────────────────────────────

    private static float mouseX = 0f;
    private static float mouseY = 0f;
    private static boolean leftClick = false;
    private static boolean rightClick = false;
    private static boolean dragging = false;
    private static float dragStartX = 0f;
    private static float dragStartY = 0f;
    private static int dragTime = 0;

    public static void updateMousePosition(float x, float y) {
        mouseX = x;
        mouseY = y;
    }

    public static void setClick(int button, boolean state) {
        if (button == 0) leftClick = state;
        else if (button == 1) rightClick = state;
    }

    public static void startDrag(float startX, float startY) {
        dragging = true;
        dragStartX = startX;
        dragStartY = startY;
        dragTime = 0;
    }

    public static void updateDragTime(int time) {
        dragTime = time;
    }

    public static void endDrag() {
        dragging = false;
    }

    public static void clearClient() {
        leftClick = false;
        rightClick = false;
        dragging = false;
        dragStartX = 0f;
        dragStartY = 0f;
        dragTime = 0;
        mouseX = 0f;
        mouseY = 0f;
    }

    // ── Getters (called from procedure blocks) ────────────────────────────────

    public static float getMouseX(Object entity) { return mouseX; }
    public static float getMouseY(Object entity) { return mouseY; }
    public static boolean isLeftClick(Object entity) { return leftClick; }
    public static boolean isRightClick(Object entity) { return rightClick; }
    public static void clearMouseLeft(Object entity) { leftClick = false; }
    public static void clearMouseRight(Object entity) { rightClick = false; }
    public static boolean isDragging(Object entity) { return dragging; }
    public static float getDragStartX(Object entity) { return dragStartX; }
    public static float getDragStartY(Object entity) { return dragStartY; }
    public static int getDragTime(Object entity) { return dragTime; }

    // ── SERVER-SIDE STATE (per-player, via network packet) ─────────────────────

    public static class ServerState {
        private static final ConcurrentHashMap<UUID, ServerPlayerData> SERVER_DATA = new ConcurrentHashMap<>();

        public static ServerPlayerData get(UUID uuid) {
            return SERVER_DATA.computeIfAbsent(uuid, k -> new ServerPlayerData());
        }

        public static void update(ServerPlayer player, float mx, float my,
                                   boolean left, boolean right, boolean drag,
                                   float dragSX, float dragSY, int dragT,
                                   String openGuiId) {
            ServerPlayerData data = get(player.getUUID());
            data.mouseX = mx;
            data.mouseY = my;
            data.leftClick = left;
            data.rightClick = right;
            data.dragging = drag;
            data.dragStartX = dragSX;
            data.dragStartY = dragSY;
            data.dragTime = dragT;
            data.openGuiId = openGuiId;
        }

        public static void clear(UUID uuid) { SERVER_DATA.remove(uuid); }

        public static float getMouseX(UUID uuid) { return get(uuid).mouseX; }
        public static float getMouseY(UUID uuid) { return get(uuid).mouseY; }
        public static boolean isLeftClick(UUID uuid) { return get(uuid).leftClick; }
        public static boolean isRightClick(UUID uuid) { return get(uuid).rightClick; }
        public static void clearMouseLeft(UUID uuid) { get(uuid).leftClick = false; }
        public static void clearMouseRight(UUID uuid) { get(uuid).rightClick = false; }
        public static boolean isDragging(UUID uuid) { return get(uuid).dragging; }
        public static float getDragStartX(UUID uuid) { return get(uuid).dragStartX; }
        public static float getDragStartY(UUID uuid) { return get(uuid).dragStartY; }
        public static int getDragTime(UUID uuid) { return get(uuid).dragTime; }
        public static String getOpenGuiId(UUID uuid) { return get(uuid).openGuiId; }
    }

    private static class ServerPlayerData {
        float mouseX, mouseY;
        boolean leftClick, rightClick;
        boolean dragging;
        float dragStartX, dragStartY;
        int dragTime;
        String openGuiId;
    }

    // ── RENDER EVENT ───────────────────────────────────────────────────────────

    /**
     * Fired on the NeoForge GAME event bus during Screen.render() when
     * a virtual GUI is open. Provides GuiGraphicsExtractor so FomekRenderer
     * overlay blocks work natively inside this event.
     */
    public static class RenderEvent extends Event {
        private final GuiGraphicsExtractor guiGraphics;
        private final Player player;
        private final int mouseX;
        private final int mouseY;
        private final int screenWidth;
        private final int screenHeight;
        private final float partialTick;

        public RenderEvent(GuiGraphicsExtractor guiGraphics, Player player,
                           int mouseX, int mouseY,
                           int screenWidth, int screenHeight, float partialTick) {
            this.guiGraphics = guiGraphics;
            this.player = player;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.partialTick = partialTick;
        }

        public GuiGraphicsExtractor getGuiGraphics() { return guiGraphics; }
        public Player getPlayer() { return player; }
        public int getMouseX() { return mouseX; }
        public int getMouseY() { return mouseY; }
        public int getScreenWidth() { return screenWidth; }
        public int getScreenHeight() { return screenHeight; }
        public float getPartialTick() { return partialTick; }
    }

    // ── MENU PART EVENT ────────────────────────────────────────────────────────

    /**
     * Fired on the NeoForge GAME event bus when VirtualGui.addMenuPart(id) is
     * called during a render pass. Lets the user split a complex GUI across
     * multiple MCreator procedures — each procedure subscribes to this event
     * and uses Register Menu Part "id" (matchMenuPart) to filter by part ID.
     *
     * The event fires synchronously within the same menu context, so all parts
     * share the same menu data, panel stack, boundaries, and elements —
     * drag/drop and shared state work across parts naturally.
     */
    public static class MenuPartEvent extends Event {
        private final GuiGraphicsExtractor guiGraphics;
        private final Player player;
        private final int mouseX;
        private final int mouseY;
        private final int screenWidth;
        private final int screenHeight;
        private final float partialTick;
        private final String partId;

        public MenuPartEvent(GuiGraphicsExtractor guiGraphics, Player player,
                             int mouseX, int mouseY,
                             int screenWidth, int screenHeight, float partialTick,
                             String partId) {
            this.guiGraphics = guiGraphics;
            this.player = player;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.partialTick = partialTick;
            this.partId = partId;
        }

        public GuiGraphicsExtractor getGuiGraphics() { return guiGraphics; }
        public Player getPlayer() { return player; }
        public int getMouseX() { return mouseX; }
        public int getMouseY() { return mouseY; }
        public int getScreenWidth() { return screenWidth; }
        public int getScreenHeight() { return screenHeight; }
        public float getPartialTick() { return partialTick; }
        public String getPartId() { return partId; }
    }
}
