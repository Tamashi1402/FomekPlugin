package net.tamashi.fomek;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.ui.BlocklyPanelRegisterDOMData;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.plugin.events.PreGeneratorsLoadingEvent;
import net.mcreator.plugin.events.workspace.WorkspaceBuildStartedEvent;
import net.mcreator.ui.MCreator;
import net.tamashi.fomek.menus.MenusRuntime;
import net.tamashi.fomek.renderer.RendererRuntime;
import net.tamashi.fomek.mixins.Launcher; // alias-free: CEM/PA Launchers referenced fully-qualified below
import net.tamashi.fomek.inveditor.parts.PluginElementTypes;
import net.tamashi.fomek.ui.CategorySeparators;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * FomekPlugin — merged all-in-one plugin (single declared javaplugin entry).
 *
 * Phase status:
 *  - Phase 2: all resource-only plugins merged (blocks, templates, triggers,
 *    lang, mappings, datalists, FomekCore api).
 *  - Phase 3: FomekRenderer runtime (RendererRuntime): injects the render API
 *    classes into <modpackage>.api.render and the fomek.mixins.json [[mixins]]
 *    entry into neoforge.mods.toml's custom-mixins user code block.
 *  - Phase 4: FomekMenus runtime (MenusRuntime): injects the full virtual GUI
 *    engine + book system into <modpackage>.api.guisystems. Registered for
 *    BOTH MCreatorLoadedEvent and WorkspaceBuildStartedEvent — the loaded
 *    event fires once per app launch, so a plugin swap without restart would
 *    otherwise leave a stale/missing runtime that generated code still
 *    imports; re-running injection before every build keeps it current.
 *  - Phase 6: CategorySeparators adds a few pixels of spacing between
 *    sub-category groups inside the Fomek toolbox category (DOM/CSS
 *    overlay via BlocklyPanelRegisterDOMData — MCreator's category JSON
 *    model has no native "insert sep here" concept; labels matched
 *    trim-vs-trim so the \u0020 sort-key padding can't break them).
 *  - Phase 5: Nerdy java plugins ported: forge mixins (mixin mod element),
 *    Nerdy's CEM (humanoidmodel + animatedmodel mod elements, modelbridge),
 *    Player Animator (workspace panel, animbridge), Inventory editor
 *    (invedit mod element + editor GUI parts).
 *    Better Animations STRIPPED per user request (used mixins, owned nothing).
 *
 * Architecture: this class is the ONLY JavaPlugin entrypoint (plugin.json
 * "javaplugin" points here). It registers all event listeners and delegates
 * to the per-subsystem runtime classes, which are plain classes so no
 * MCreator plugin loader assumptions are needed for them.
 */
public class FomekPlugin extends JavaPlugin {

    private static final Logger LOG = LogManager.getLogger("Fomek Plugin");

    private final RendererRuntime rendererRuntime = new RendererRuntime();
    private final MenusRuntime menusRuntime = new MenusRuntime();

    public FomekPlugin(Plugin plugin) {
        super(plugin);

        // ── Mod element type registrations (all in one pre-generators pass) ──
        addListener(PreGeneratorsLoadingEvent.class, event -> {
            Launcher.registerElementTypes();                       // forge mixins: "mixin"
            net.tamashi.fomek.cem.Launcher.registerElementTypes(); // CEM: "humanoidmodel", "animatedmodel"
            PluginElementTypes.load();                           // inventory editor: "invedit"
        });

        // ── Blockly JS bridges (CEM model editor + player animation picker) ──
        addListener(BlocklyPanelRegisterDOMData.class, event -> {
            net.tamashi.fomek.cem.Launcher.onBlocklyPanelRegister(event); // "modelbridge"
            net.tamashi.fomek.animator.Launcher.onBlocklyPanelRegister(event); // "animbridge"
            CategorySeparators.onBlocklyPanelRegister(event); // Fomek category group spacing
            net.tamashi.fomek.menus.StudioAssets.register(event);
        });

        // ── Workspace loaded: runtimes + player animator panel ──
        addListener(MCreatorLoadedEvent.class, event -> {
            MCreator mcreator = event.getMCreator();
            rendererRuntime.onMCreatorLoaded(mcreator);
            menusRuntime.onMCreatorLoaded(mcreator);
            net.tamashi.fomek.animator.Launcher.onMCreatorLoaded(mcreator);
        });

        // MenusRuntime must also run on every build start (see MenusRuntime docs)
        addListener(WorkspaceBuildStartedEvent.class, event ->
                menusRuntime.onBuildStarted(event.getMCreator()));

        LOG.info("FomekPlugin merged build loaded (phases 2-5: resources, renderer/menus runtimes "
                + "mixins/CEM/player-animator/inventory-editor subsystems; better animations stripped");
    }
}
