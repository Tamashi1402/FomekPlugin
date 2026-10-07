package ${package}.client;

public class ClientHelper {
    public static void openInventory() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new InventoryScreen(mc.player));
    }
}