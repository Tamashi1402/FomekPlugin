package net.tamashi.fomek.mixins;

import net.mcreator.element.ModElementType;
import net.tamashi.fomek.mixins.element.Mixin;
import net.tamashi.fomek.mixins.element.MixinGUI;

import static net.mcreator.element.ModElementTypeLoader.register;

/**
 * Ported from Forge-mixins-plugin-MCreator Launcher (phase 5).
 * Registration is wired in FomekPlugin's PreGeneratorsLoadingEvent listener.
 */
public class Launcher {

    public static void registerElementTypes() {
        register(new ModElementType<>("mixin", 'M', MixinGUI::new, Mixin.class));
    }

}
