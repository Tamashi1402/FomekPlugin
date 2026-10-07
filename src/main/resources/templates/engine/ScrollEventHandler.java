package net.tamashi.fomekcore.api.guisystems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * ScrollEventHandler — captures mouse wheel events and forwards them to
 * VirtualGui for scroll view handling, and to InputManager for custom
 * scroll-up/scroll-down action routing.
 *
 * Only active when a virtual GUI menu is open. When a scroll view is under
 * the mouse cursor, the scroll delta is applied to that scroll view and the
 * event is cancelled (prevents vanilla hotbar scroll).
 *
 * Regardless of whether a scroll view consumed it, the delta is always fed
 * to InputManager so scroll-based actions (e.g. zoom, weapon switch) work.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class ScrollEventHandler {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!VirtualGui.isAnyOpen()) return;

        float mx = (float) event.getMouseX();
        float my = (float) event.getMouseY();

        if (MenuControls.scroll(mx, my, event.getScrollDeltaX(), event.getScrollDeltaY())) { event.setCanceled(true); return; }

        // Feed scroll delta to InputManager (for scroll_up/scroll_down actions)
        InputManager.feedScrollDelta(event.getScrollDeltaY());

        if (VirtualGui.handleMouseScroll(event.getScrollDeltaY(), mx, my)) {
            // A scroll view consumed the scroll — cancel vanilla behavior
            // (prevents hotbar item cycling while scrolling a menu)
            event.setCanceled(true);
        }
    }
}
