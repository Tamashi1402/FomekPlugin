package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * GuiStatePayload — Client→Server synchronization packet.
 *
 * Sent periodically from the client to the server to sync mouse state
 * and the currently open virtual GUI ID. This allows server-side
 * procedures to check mouse state and which menu is open.
 */
public class GuiStatePayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GuiStatePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.parse("@FOMEK_MODID@:gui_state_sync"));

    @SuppressWarnings("unchecked")
    public static final StreamCodec<RegistryFriendlyByteBuf, GuiStatePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> payload.encode(buf),
            (buf) -> GuiStatePayload.decode(buf)
        );

    private final float mouseX, mouseY;
    private final boolean leftClick, rightClick;
    private final boolean dragging;
    private final float dragStartX, dragStartY;
    private final int dragTime;
    private final String openGuiId;

    public GuiStatePayload(float mouseX, float mouseY, boolean leftClick, boolean rightClick,
                           boolean dragging, float dragStartX, float dragStartY, int dragTime,
                           String openGuiId) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.dragging = dragging;
        this.dragStartX = dragStartX;
        this.dragStartY = dragStartY;
        this.dragTime = dragTime;
        this.openGuiId = openGuiId != null ? openGuiId : "";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(mouseX);
        buf.writeFloat(mouseY);
        buf.writeBoolean(leftClick);
        buf.writeBoolean(rightClick);
        buf.writeBoolean(dragging);
        buf.writeFloat(dragStartX);
        buf.writeFloat(dragStartY);
        buf.writeInt(dragTime);
        buf.writeUtf(openGuiId, 128);
    }

    public static GuiStatePayload decode(FriendlyByteBuf buf) {
        return new GuiStatePayload(
            buf.readFloat(), buf.readFloat(),
            buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readFloat(),
            buf.readFloat(), buf.readInt(),
            buf.readUtf(128)
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── Getters ─────────────────────────────────────────────────────────────────

    public float getMouseX() { return mouseX; }
    public float getMouseY() { return mouseY; }
    public boolean isLeftClick() { return leftClick; }
    public boolean isRightClick() { return rightClick; }
    public boolean isDragging() { return dragging; }
    public float getDragStartX() { return dragStartX; }
    public float getDragStartY() { return dragStartY; }
    public int getDragTime() { return dragTime; }
    public String getOpenGuiId() { return openGuiId; }
}
