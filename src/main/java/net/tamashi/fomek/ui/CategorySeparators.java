package net.tamashi.fomek.ui;

import net.mcreator.plugin.events.ui.BlocklyPanelRegisterDOMData;
import net.mcreator.ui.init.L10N;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CategorySeparators — injects a thin divider line between groups of
 * sub-categories inside the "Fomek" root category of the Blockly
 * toolbox tree (procedure/trigger/condition editors), so the group
 * breaks from PLAN.md section 3 read at a glance.
 *
 * WHY: MCreator's category data model ($<id>.json files, loaded by the
 * closed-source net.mcreator.blockly.data.ExternalBlockLoader) has no
 * "insert a <sep> here" concept — generateCategoryXML() only ever emits
 * <category> wrappers for our json category defs, nothing else. Blockly's
 * toolbox XML format itself DOES support <sep> as a sibling of <category>,
 * but we have no way to make MCreator's generator emit one from pure
 * category-json config.
 *
 * HOW: BlocklyPanelRegisterDOMData (the same event CEM/PA already use for
 * their JS bridges) lets a plugin run arbitrary JS/CSS inside the live
 * Blockly WebView whenever a Blockly panel is (re-)created. We use that to
 * post-process the ALREADY-RENDERED toolbox tree: find the "Fomek" node,
 * walk its direct child category rows (confirmed DOM shape for this
 * MCreator build: classic goog.ui.tree markup — .blocklyTreeRow /
 * .blocklyTreeLabel rows, each wrapped in a [role=treeitem] node, with a
 * [role=group] children container holding the next nesting level), and
 * insert a plain divider <div> (thin horizontal rule) before each group
 * boundary, indented to match the tree rows.
 *
 * Labels are resolved via L10N at injection time (with raw-name fallback,
 * same behavior as ToolboxCategory.getName()) so matching keeps working if
 * the category labels ever get translated — currently all lang files ship
 * English values for them.
 *
 * This is a cosmetic DOM overlay, not a real Blockly toolbox item — it
 * carries no role/behavior, just a line. A MutationObserver re-applies it
 * whenever the toolbox tree is torn down/rebuilt (search filter, workspace
 * switch, theme change, collapse/expand).
 *
 * Grouping matches PLAN.md section 3 (v3.3.0 root order, breaks before
 * World, Menus):
 *   Math, Files, Utils, Event Specific
 *   --- break --- World, Player, Entity, Items
 *   --- break --- Menus, Render, Entity Models, Chunk
 * (Superpowers, Multiverse, Time Travel, Perception and Dimensions moved to
 * the separate "Fomek Core" plugin in v3.3.0 — no more bottom group here.)
 */
public final class CategorySeparators {

    private static final Logger LOG = LogManager.getLogger("Fomek Category Separators");

    // Categories that get a divider line inserted immediately BEFORE them.
    // Each entry: (lang key, fallback raw name).
    private static final String[][] BREAK_BEFORE = {
            {"blockly.category.fomek_world", "World"},
            {"blockly.category.fomek_menus", "Menus"}
    };

    // Root category label, resolved the same way.
    private static final String[] FOMEK_ROOT = {"blockly.category.fomek", "Fomek"};

    private CategorySeparators() {
    }

    public static void onBlocklyPanelRegister(BlocklyPanelRegisterDOMData event) {
        try {
            event.addCSSToDOM(CSS);
            String rootLabel = resolveLabel(FOMEK_ROOT);
            String[] breakLabels = new String[BREAK_BEFORE.length];
            for (int i = 0; i < BREAK_BEFORE.length; i++)
                breakLabels[i] = resolveLabel(BREAK_BEFORE[i]);
            event.executeScript(buildJS(rootLabel, breakLabels));
        } catch (Exception e) {
            LOG.warn("Failed to inject Fomek category separators", e);
        }
    }

    private static String resolveLabel(String[] langKeyAndFallback) {
        // TRIM is load-bearing: root-child labels carry invisible leading-space
        // sort keys (see PLAN.md "Root ordering hack") that must not leak into
        // the JS matching below — the DOM text is compared trimmed.
        String localized = L10N.t(langKeyAndFallback[0]);
        String raw = localized != null ? localized : langKeyAndFallback[1];
        return raw == null ? null : raw.trim();
    }

    private static final String CSS = ""
            + ".fomek-cat-sep {"
            + "  height: 0;"
            + "  margin: 5px 12px 5px 0;"
            + "  border-top: 1px solid rgba(128,128,128,0.4);"
            + "  pointer-events: none;"
            + "}";

    // Language note: this runs inside the Blockly WebView, vanilla JS only
    // (no bridge calls needed). Idempotent + self-healing via MutationObserver
    // because MCreator tears down and rebuilds the tree on search/filter,
    // workspace switch and theme changes.
    private static String buildJS(String rootLabel, String[] breakLabels) {
        return ""
                + "(function() {"
                + "  var BREAK_BEFORE = " + jsArray(breakLabels) + ";"
                + "  var ROOT = " + jsEscape(rootLabel) + ";"
                + "  function applyOnce() {"
                + "    var labels = document.querySelectorAll('.blocklyTreeLabel');"
                + "    var fomekLabel = null;"
                + "    for (var i = 0; i < labels.length; i++) {"
                + "      if (labels[i].textContent.trim() === ROOT) { fomekLabel = labels[i]; break; }"
                + "    }"
                + "    if (!fomekLabel) return false;"
                + "    var fomekNode = fomekLabel.closest('[role=\"treeitem\"]');"
                + "    if (!fomekNode) return false;"
                + "    var childrenContainer = fomekNode.querySelector('[role=\"group\"]');"
                + "    if (!childrenContainer) return false;"
                + "    if (childrenContainer.getAttribute('data-fomek-sep-done') === '1') return true;"
                + "    var directChildren = [];"
                + "    for (var c = 0; c < childrenContainer.children.length; c++) {"
                + "      var el = childrenContainer.children[c];"
                + "      if (el.getAttribute && el.getAttribute('role') === 'treeitem') directChildren.push(el);"
                + "    }"
                + "    var inserted = 0;"
                + "    for (var j = 0; j < directChildren.length; j++) {"
                + "      var child = directChildren[j];"
                + "      var row = child.querySelector('.blocklyTreeRow');"
                + "      var label = child.querySelector('.blocklyTreeLabel');"
                + "      if (!row || !label) continue;"
                + "      var text = label.textContent.trim();"
                + "      if (BREAK_BEFORE.indexOf(text) !== -1) {"
                + "        var sep = document.createElement('div');"
                + "        sep.className = 'fomek-cat-sep';"
                + "        var pad = row.style.paddingLeft;"
                + "        if (pad) sep.style.marginLeft = pad;"
                + "        childrenContainer.insertBefore(sep, child);"
                + "        inserted++;"
                + "      }"
                + "    }"
                + "    childrenContainer.setAttribute('data-fomek-sep-done', '1');"
                + "    return inserted > 0;"
                + "  }"
                + "  if (!applyOnce()) {"
                + "    setTimeout(applyOnce, 300);"
                + "    setTimeout(applyOnce, 1000);"
                + "  }"
                + "  var toolboxRoot = document.body;"
                + "  var observer = new MutationObserver(function(mutations) {"
                + "    clearTimeout(window.__fomekSepTimer);"
                + "    window.__fomekSepTimer = setTimeout(applyOnce, 150);"
                + "  });"
                + "  observer.observe(toolboxRoot, { childList: true, subtree: true });"
                + "})();";
    }

    private static String jsArray(String[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(jsEscape(values[i]));
        }
        return sb.append("]").toString();
    }

    private static String jsEscape(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
