package net.tamashi.fomek.menus;

import java.nio.file.*;

/** Read-only resource bridge checks; does not start MCreator or Minecraft. */
public class StudioAssetsTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of(args[0]),"studio-assets-");
        Path asset=root.resolve("assets/test/font/demo.json");Files.createDirectories(asset.getParent());
        Files.writeString(asset,"{\"providers\":[]}");StudioAssets bridge=new StudioAssets(root);
        if(!bridge.asset("test:font/demo.json").equals("{\"providers\":[]}"))throw new AssertionError("Workspace resource");
        Files.writeString(asset,"{}");if(!bridge.asset("test:font/demo.json").contains("providers"))throw new AssertionError("Cache");
        bridge.refresh();if(!bridge.asset("test:font/demo.json").equals("{}"))throw new AssertionError("Refresh");
        if(!bridge.asset("test:../../secret.json").isEmpty()||!bridge.asset("C:/secret.json").isEmpty()||!bridge.asset("test:font/demo.java").isEmpty())throw new AssertionError("Resource path boundary");
        String vanilla=bridge.asset("minecraft:font/default.json");
        if(vanilla.isEmpty())throw new AssertionError("Missing cached vanilla font resource");
        System.out.println("PASS: workspace assets, cache refresh, resource path boundary and cached Minecraft font JSON");
    }
}
