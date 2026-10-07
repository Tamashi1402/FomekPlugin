package net.tamashi.fomek.cem;

import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.plugin.events.ui.BlocklyPanelRegisterDOMData;
import net.tamashi.fomek.cem.elements.AnimatedModel;
import net.tamashi.fomek.cem.elements.AnimatedModelGUI;
import net.tamashi.fomek.cem.elements.HumanoidModel;
import net.tamashi.fomek.cem.elements.HumanoidModelGUI;
import net.tamashi.fomek.cem.parts.PluginJavascriptBridge;

/**
 * Ported from MCreator-NerdysCustomEntityModels Launcher (phase 5).
 * Registration is wired in FomekPlugin's PreGeneratorsLoadingEvent and
 * BlocklyPanelRegisterDOMData listeners. Statics kept on this class since
 * workspace parts may reference them.
 */
public class Launcher {
    public static ModElementType<?> HUMANOIDMODEL;
    public static ModElementType<?> ANIMATEDMODEL;
    public static PluginJavascriptBridge pluginJavascriptBridge = null;

    public static void registerElementTypes() {
        HUMANOIDMODEL = ModElementTypeLoader.register(new ModElementType<>("humanoidmodel", (Character) 'H', HumanoidModelGUI::new, HumanoidModel.class));
        ANIMATEDMODEL = ModElementTypeLoader.register(new ModElementType<>("animatedmodel", (Character) 'A', AnimatedModelGUI::new, AnimatedModel.class));
    }

    public static void onBlocklyPanelRegister(BlocklyPanelRegisterDOMData event) {
        pluginJavascriptBridge = new PluginJavascriptBridge(event.getBlocklyPanel().getMCreator());
        event.addJavaScriptBridge("modelbridge", pluginJavascriptBridge);
    }
}
