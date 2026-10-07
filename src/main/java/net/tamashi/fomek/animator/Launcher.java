package net.tamashi.fomek.animator;

import net.mcreator.plugin.events.ui.BlocklyPanelRegisterDOMData;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.variants.modmaker.ModMaker;
import net.tamashi.fomek.animator.parts.PluginJavascriptBridge;
import net.tamashi.fomek.animator.parts.WorkspacePanelPlayerAnimations;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ported from MCreator-Player-Animator Launcher (phase 5).
 * Registration is wired in FomekPlugin's BlocklyPanelRegisterDOMData and
 * MCreatorLoadedEvent listeners. Statics kept on this class since the parts
 * classes reference them (Launcher.animations, Launcher.pluginJavascriptBridge).
 */
public class Launcher {

    public static PluginJavascriptBridge pluginJavascriptBridge = null;
    public static List<String> animations = new ArrayList<>();

    public static void onBlocklyPanelRegister(BlocklyPanelRegisterDOMData event) {
        pluginJavascriptBridge = new PluginJavascriptBridge(event.getBlocklyPanel().getMCreator());
        event.addJavaScriptBridge("animbridge", pluginJavascriptBridge);
    }

    public static void onMCreatorLoaded(MCreator mcreator) {
        SwingUtilities.invokeLater(() -> {
            if (mcreator instanceof ModMaker modmaker) {
                WorkspacePanelPlayerAnimations panel = new WorkspacePanelPlayerAnimations(modmaker.getWorkspacePanel());
                panel.setOpaque(false);
                modmaker.getWorkspacePanel().resourcesPan.addResourcesTab(L10N.t("workspacepanel.player_animations", new Object[0]), panel);
                File animDir = panel.getAnimationsDir(mcreator);
                if (animDir.exists() && animDir.isDirectory()) {
                    File[] files = animDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) {
                        animations.clear();
                        for (File file : files) {
                            animations.addAll(panel.parseAnimations(file));
                        }
                    }
                }
            }
        });
    }

}
