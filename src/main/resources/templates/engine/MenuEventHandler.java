package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;

/**
 * MenuEventHandler — Replaces mixins with NeoForge ScreenEvent handlers.
 *
 * Hooks into every Screen via NeoForge events (no mixins needed):
 *   - MouseButtonPressed.Pre  → set click state, start panel drag
 *   - MouseButtonReleased.Pre → end panel drag
 *   - MouseDragged.Pre        → handle panel dragging
 *   - Render.Post             → update mouse pos, set up render context, fire RenderEvent
 *   - Closing                 → clear all state, close virtual GUI
 *
 * Sets up FomekRenderer context via reflection so FomekRenderer
 * overlay blocks work natively inside the Menu System trigger.
 * Also updates MenuRenderHelper so native FomekMenu render blocks work.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class MenuEventHandler {

    // ── FomekRenderer context setup (reflection, soft dependency) ───────────────

    private static boolean fomekRendererChecked = false;
    private static Method fomekRendererSetup = null;

    private static void setupFomekRendererContext(GuiGraphicsExtractor guiGraphics) {
        if (!fomekRendererChecked) {
            fomekRendererChecked = true;
            try {
                Class<?> renderAPI = Class.forName(MenuEventHandler.class.getPackage().getName()
                        .replace(".api.guisystems", ".api.render") + ".RenderAPI");
                for (String methodName : new String[]{
                        "setCurrentGuiGraphics", "setGuiGraphics",
                        "setCurrentContext", "setRenderContext",
                        "beginRender", "setOverlayGuiGraphics"
                }) {
                    try {
                        fomekRendererSetup = renderAPI.getMethod(methodName, GuiGraphicsExtractor.class);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Exception ignored) {}
        }
        if (fomekRendererSetup != null) {
            try {
                fomekRendererSetup.invoke(null, guiGraphics);
            } catch (Exception ignored) {}
        }
    }

    // ── Network sync (send mouse state to server) ──────────────────────────────

    private static void sendSyncPacket() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new GuiStatePayload(
                GuiState.getMouseX(null), GuiState.getMouseY(null),
                GuiState.isLeftClick(null), GuiState.isRightClick(null),
                GuiState.isDragging(null), GuiState.getDragStartX(null),
                GuiState.getDragStartY(null), GuiState.getDragTime(null),
                VirtualGui.getCurrentId()
            )));
        }
    }

    // ── Mouse click ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (VirtualGui.isAnyOpen() && MenuControls.click(event.getMouseX(), event.getMouseY(), event.getButton())) { event.setCanceled(true); return; }
        GuiState.setClick(event.getButton(), true);
        if (event.getButton() == 0) {
            GuiState.startDrag((float) event.getMouseX(), (float) event.getMouseY());
            if (VirtualGui.isAnyOpen()) {
                VirtualGui.handleClick((float) event.getMouseX(), (float) event.getMouseY(), 0);
            }
        }
        if (event.getButton() == 1) {
            if (VirtualGui.isAnyOpen()) {
                VirtualGui.handleRightClick((float) event.getMouseX(), (float) event.getMouseY());
            }
        }
        sendSyncPacket();
    }

    // ── Mouse release ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        StudioRuntime.release();
        GuiState.setClick(event.getButton(), false);
        if (event.getButton() == 0) {
            VirtualGui.handleRelease();
            GuiState.endDrag();
        }
        sendSyncPacket();
    }

    @SubscribeEvent
    public static void onStudioKey(ScreenEvent.KeyPressed.Pre event) {
        if (VirtualGui.isAnyOpen() && MenuControls.key(event.getKeyCode(), event.getScanCode(), event.getModifiers())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void onStudioCharacter(ScreenEvent.CharacterTyped.Pre event) {
        if (VirtualGui.isAnyOpen() && MenuControls.typed(event.getCodePoint(), event.getModifiers())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void onStudioScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (VirtualGui.isAnyOpen() && MenuControls.scroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY())) event.setCanceled(true);
    }

    // ── Mouse drag ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (MenuControls.drag(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) { event.setCanceled(true); return; }
        GuiState.updateMousePosition((float) event.getMouseX(), (float) event.getMouseY());
        if (event.getMouseButton() == 0) {
            GuiState.updateDragTime(GuiState.getDragTime(null) + 1);
            if (VirtualGui.isAnyOpen()) {
                VirtualGui.handleDrag((float) event.getMouseX(), (float) event.getMouseY());
            }
        }
    }

    // ── Screen render → set up context, fire RenderEvent, render elements ─────────

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        GuiGraphicsExtractor guiGraphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        float partialTick = event.getPartialTick();

        // Update mouse position
        GuiState.updateMousePosition((float) mouseX, (float) mouseY);

        // Set up MenuRenderHelper so native render blocks have context
        MenuRenderHelper.setGuiGraphics(guiGraphics);

        // Set up FomekRenderer context so its overlay blocks work inside our event
        setupFomekRendererContext(guiGraphics);

        // Fire the RenderEvent (this triggers the user's Menu System procedure)
        VirtualGui.onScreenRender(guiGraphics, mouseX, mouseY, partialTick);

        // Render virtual GUI elements (panels, buttons, etc.)
        VirtualGui.renderElements(guiGraphics, mouseX, mouseY, partialTick);

        // Clear the render context after rendering
        MenuRenderHelper.clear();
    }

    // ── Screen closing → clear all state ─────────────────────────────────────────

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        VirtualGui.close();
        GuiState.clearClient();
        InputManager.clearAll();
        UpdateManager.clearAll();
        sendSyncPacket();
    }

    // ── Client tick → poll tick-frequency InputManagers ───────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (VirtualGui.isAnyOpen()) {
            InputManager.updateTick();
            UpdateManager.onTick();
        }
    }

    // ── Network packet registration ──────────────────────────────────────────────

    @EventBusSubscriber(value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onReloadListeners(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
            event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("${modid}", "menu_text_clear"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(MenuText::clear));
        }
        @SubscribeEvent
        public static void onRegisterPayload(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("1");
            registrar.playToServer(
                GuiStatePayload.TYPE,
                GuiStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                            GuiState.ServerState.update(sp,
                                payload.getMouseX(), payload.getMouseY(),
                                payload.isLeftClick(), payload.isRightClick(),
                                payload.isDragging(), payload.getDragStartX(),
                                payload.getDragStartY(), payload.getDragTime(),
                                payload.getOpenGuiId()
                            );
                        }
                    });
                }
            );
        }
    }
}
