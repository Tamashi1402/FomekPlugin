# FomekPlugin — Master Merge Plan

**Goal:** one big SRC Java plugin `fomekplugin` for MCreator 2026.1, targeting **NeoForge 1.21.1 only**, merging all listed plugins. One plugin to update when MC/MCreator versions move.

**Rule:** every non-1.21.1 generator folder (forge-1.20.1, neoforge-1.21.4, neoforge-1.21.8, …) is dropped. Only `neoforge-1.21.1/` survives.

---

## 1. Inventory (verified from the archives)

| # | Source | Type | Block JSONs | .ftl templates | Triggers | SRC available |
|---|--------|------|------------|----------------|----------|----------------|
| 1 | FomekPlugin v0.1 (base, dimensions/multiverse/time/perception/input/superpowers/chunk) | resource | 161 | 146 | 19 | ❌ build-only (decompile classes if needed; only 1 plugin.json + resources, no .class → fully recoverable) |
| 2 | FomekMenus v0.1 | **java** | 161 | 152 | 2 | ✅ attached SRC |
| 3 | FomekRenderer v0.1 | **java** | 253 | 240 | 3 | ✅ attached SRC |
| 4 | attribute_modifiers | resource | 15 | 26 | 4 | ✅ (plain plugin = src by definition) |
| 5 | file-manager | resource | 72 | 134 | 0 | ✅ |
| 6 | ItemTooltips | resource | 9 | 14 | 4 | ✅ |
| 7 | MathUtils | resource | 202 | 157 | 0 | ✅ |
| 8 | chunk-manager | resource | 12 | 33 | 0 | ✅ |
| 9 | Nerdys Custom Entity Models (CEM) | **java** | 38 | 77 | 18 | ✅ GitHub 2026.1 branch (adds AnimatedModel/HumanoidModel mod element types) |
| 10 | Nerdy's Player Animator | **java** | 5 | 8 | 0 | ✅ GitHub 2026.1 (adds workspace UI panel + mixins) |
| 11 | Forge mixins | **java** | 0 | 0 | 0 | ✅ GitHub 2026.1 (adds **Mixin mod element type** — CEM + Player Animator depend on it) |
| 12 | Nerdy's better animations | **java** | 2 | 6 | 0 | ✅ GitHub 2026.1 |
| 13 | Inventory-editor-MCreator | **java?** | ? | ? | ? | ✅ GitHub 2026.1 — **no matching zip in plugins.rar, need to know what it contributes** |

**In plugins.rar but NOT in the category plan (pending decision):**

| Plugin | Blocks | .ftl | Triggers |
|--------|--------|------|----------|
| Marwinekks Procedures Plus 1.15.0 | 40 | 346 | 12 |
| ProceduresExtras 0.1.1 | 15 | 47 | 0 |
| SnailsPlugin 2.0.2 | 239 | 1432 | 243 |
| RedWires Plugin | 114 | 705 | 88 |

All four already have neoforge-1.21.1 generators, so merging is mechanically easy — it's a scope question, not a technical one.

---

## 2. Target project

```
fomekplugin/                      ← Gradle SRC project (same build.gradle pattern as FomekMenus/Renderer)
├─ build.gradle                   ← java plugin, jar → fomek-plugin.zip
├─ settings.gradle
├─ src/main/java/net/tamashi/fomek/
│   ├─ FomekPlugin.java           ← entry: extends JavaPlugin, wires all subsystems
│   ├─ menus/…                    ← from FomekMenusPlugin.java
│   ├─ renderer/…                 ← from FomekRendererPlugin.java
│   ├─ cem/… entitymodels/…       ← from CEM (mod element types + GUIs)
│   ├─ playeranimator/…           ← from Player Animator (workspace panel)
│   └─ mixins/…                    ← from forge mixins (Mixin mod element)
└─ src/main/resources/
    ├─ plugin.json                ← id: fomekplugin, supportedversions: [2026001]
    ├─ apis/fomekcore_plugin.yaml ← kept (FomekCore cursemaven dep)
    ├─ blockly/js/*.js            ← merged, dedupe HUE vars + extensions
    ├─ lang/texts.properties      ← merged; all `blockly.category.*` keys rewritten to the new tree
    ├─ procedures/*.json          ← block defs (machine names UNCHANGED)
    ├─ triggers/*.json
    ├─ templates/*.java           ← runtime injection templates (menus engine, book, PageFlip, RenderAPI…)
    ├─ variables/, features/, datalists/, help/, icons/, themes/  ← merged as needed
    └─ neoforge-1.21.1/
        ├─ generator.yaml         ← MERGED (renderer, CEM, PA, mixins each ship one)
        ├─ mappings/*.yaml        ← merged types.yaml + fomek_rendertypes.yaml + others
        ├─ procedures/*.ftl       ← all procedure templates
        ├─ triggers/
        ├─ variables/
        ├─ base_templates via generator.yaml
        └─ apis/
```

**Build**: same `project(':MCreator')` linked Gradle setup the two SRC plugins already use (mcreator_path in gradle.properties). One jar: `fomek-plugin.zip`.

---

## 3. Category tree (final palette)

Toolbox text keys live in lang: `blockly.category.<id>=Label`. Break lines are natural — MCreator renders categories in lang-definition order with the existing `<break>`-style separators possible via `blockly.category` structure. Sub-category ids get the `fomek_` prefix.

```
Fomek  (main category, dark purple  — hue ~260, "Fomek" branding)
  Actions          ← unassigned action blocks from all merged plugins
  Data             ← unassigned data blocks
  Utils            ← unassigned utility blocks
  Math             ← MathUtils fully
  Files            ← file-manager fully
  <break>
  Attribute Modifiers ← attribute_modifiers fully
  Items            ← ItemTooltips fully
  Player           ← Player Animator blocks (+ better animations?)
  Entity           ← CEM generic entity/model blocks
  <break>
  Chunk            ← FomekPlugin chunk blocks (TRUTH) + chunk-manager (deduped against Fomek's)
  Dimensions (REWORK) ← FomekPlugin dimensions
  Multiverse (REWORK) ← FomekPlugin multiverse
  Time (REWORK)       ← FomekPlugin spacetime relabeled
  Perception (REWORK) ← FomekPlugin perception
  <break>
  Render           ← FomekRenderer fully + CEM render/model blocks
  Animations       ← Player Animator fully (+ better animations)
  <break>
  Menus (REWORK)   ← FomekMenus, reworked sub-toolboxes:
                       building / objects / attributes / data / menudata / book / render / interactions
  Input (REWORK)   ← FomekPlugin input
  Superpowers (REWORK) ← FomekPlugin superpowers
```

**(REWORK)** stays literally in the category label as the user's own to-do indicator.

**Divider lines:** implemented 2026-10-06 via `net.tamashi.fomek.ui.CategorySeparators` (cosmetic DOM/CSS injection through `BlocklyPanelRegisterDOMData`, same hook CEM/PA use for their JS bridges). MCreator's `$<id>.json` category model has no native "insert a line here" concept — confirmed by reading `ExternalBlockLoader.generateCategoryXML` in MCreator 2026.1.14619 source: it only ever emits `<category>` wrappers for our json defs. So the 4 breaks above are rendered client-side: a MutationObserver finds the "Fomek" tree node's direct child rows and inserts a thin divider `<div>` before Attribute Modifiers / Chunk / Render / Menus (REWORK) (labels resolved via L10N at injection time with raw-name fallback, so future translations don't break matching). Confirmed DOM shape for this MCreator build (classic goog.ui.tree markup, `.blocklyTreeRow` / `.blocklyTreeLabel`, `[role=treeitem]` / `[role=group]`) via mcreator_blockly.css in the real MCreator 2026.1.14619 source — not guessed. Self-heals on toolbox re-render (search, workspace switch, theme change).

Source → target toolbox mapping (toolbox_id values rewritten in each block JSON):

| Source toolbox | New toolbox_id | Label |
|---|---|---|
| fomek_plugin_actions | fomek_actions | Actions |
| (new bucket) | fomek_data / fomek_utils | Data / Utils |
| mathutils, mathdataelement, mathdatalist, mathdatamap, mathvector*, mathrandomsource, mathnbt, mathnumber, mathothers | fomek_math (sub-groups preserved via group order) | Math |
| file-manager toolboxes | fomek_files | Files |
| attribute_modifiers toolboxes | fomek_attrmods | Attribute Modifiers |
| ItemTooltips toolboxes | fomek_items | Items |
| player animator toolboxes | fomek_player | Player |
| CEM entity/model toolboxes | fomek_entity | Entity |
| fomek_plugin chunk blocks + chunk-manager toolboxes | fomek_chunk | Chunk |
| fomek_plugin_dimensions | fomek_dimensions | Dimensions (REWORK) |
| fomek_plugin_multiverse | fomek_multiverse | Multiverse (REWORK) |
| fomek_plugin_spacetime | fomek_time | Time (REWORK) |
| fomek_plugin_perception | fomek_perception | Perception (REWORK) |
| all fomek_menu_* + renderer sub-toolboxes | fomek_render / fomek_menus / fomek_input / fomek_superpowers | … |

Variable/marker blocks with no toolbox_id (`<root>` = 36+15 blocks across menus/renderer) stay root-invisible — they're mutator/extension blocks, not palette blocks.

---

## 4. ID & namespace policy (the important one)

- **Machine names stay unchanged**: `block type="fomekmenu_book_flip"`, `datalist_add`, `blockstate_to_datamap`, etc. keep their exact names. Reason: existing workspaces (.mcprojects) that use these blocks keep loading and compiling after the swap. Only the palette location (toolbox_id + lang keys) changes. Renaming everything to a fomek_* scheme would break every existing project for zero gain.
- Block JSON filenames, .ftl filenames, trigger registry names: all unchanged.
- `Blockly.Msg.*_HUE` constants: dedupe/merge in blockly/js (several plugins define the same constant names — verify no value clashes).
- lang `texts.properties`: merge with per-section headers; abort the build script if duplicate keys with different values are found (content assert, same convention as MacroForge).

## 5. Collision / conflict register (found during survey)

1. **Mixins configs**: FomekRenderer deliberately uses its own `fomek.mixins.json` because forge_mixins and player_animator *overwrite* `<modid>.mixins.json`. Merged plugin owns all of them now → keep separate config files, keep the [[mixins]] user-code-block injection for each. No behavior change.
2. **Chunk**: FomekPlugin chunk blocks are truth; chunk-manager blocks are merged in, duplicates dropped in FomekPlugin's favor (names that exist in both: Fomek's version wins; chunk-manager-only blocks keep their names).
3. **Runtime injection**: FomekMenus injects `<modpackage>.api.guisystems`, FomekRenderer injects `<modpackage>.api.render`, each with its own stamp file + version. Keep both subsystems with separate stamps, merged into one JavaPlugin with per-subsystem version constants.
4. **generator.yaml**: renderer, CEM, player animator, forge mixins each ship base_templates + mixins entries. Merge into one yaml, dedupe identical entries, keep all mixin configs distinct.
5. **Mod element types**: CEM's AnimatedModel/HumanoidModel + forge mixins' Mixin element are Java-side registry items — port their element classes + GUIs into the merged plugin's java sources (from GitHub SRC), package `net.nerdypuzzle.*` kept as-is to avoid touching their internal reflection.
6. **Datalists**: attribute_modifiers, CEM, Snails, redwires ship datalists/ — merge per-file, abort on conflicting same-name files.
7. **ItemTooltips vs attribute_modifiers**: both touch itemstack modifiers/tooltip areas — block names don't collide (checked: 9 vs 15 JSONs, zero name overlap), but their ftl both add itemstack tooltip event code; verify the generated-code merge point (they should append, not overwrite — check triggers).
8. **FomekCore dep**: `apis/fomekcore_plugin.yaml` (curse.maven fomek-core) stays — FomekPlugin blocks compile against FomekCore at runtime.

## 6. Build order (phases)

1. **Scaffold** — Gradle project, plugin.json (`fomekplugin`, 2026001, version 3.0.0), lang skeleton, empty java entry.
2. **Resource-only plugins first** (no java, lowest risk): MathUtils, file-manager, ItemTooltips, attribute_modifiers, chunk-manager + FomekPlugin v0.1 resources (incl. apis). Category remap + lang merge + ftl copy. → buildable zip, palette tree working.
3. **Java subsystem: FomekRenderer** — port plugin class, generator.yaml merge, mixins injection.
4. **Java subsystem: FomekMenus** — port plugin class, engine templates, stamp logic.
5. **Nerdy java plugins from GitHub SRC** — forge mixins (element type), CEM (elements + datalists + blocks), player animator (panel + blocks), better animations. Clone 2026.1 branches, port only neoforge-1.21.1 parts.
6. **Chunk reconciliation** (Fomek truth vs chunk-manager).
7. **Polish pass**: dedupe hues, unify lang comment style, documentation.md, version stamp, smoke-build with stubbed MCreator (Flask-test-client convention like MacroForge: build jar from resources only, validate JSON/yaml/ftl parse, assert no dup lang keys).
8. **(pending)** Snails / Procedures Plus / ProceduresExtras / RedWires if in scope.

## 7. Open questions (blocking before phase 2+)

1. **SnailsPlugin, Procedures Plus, ProceduresExtras, RedWires** — in the rar but not in the category plan. Merge into Actions/Data/Utils (+ leftover buckets) or leave out of v1?
2. **forge mixins** — take fully (needed as Mixin mod element for CEM + Player Animator) and **better animations** — into Animations? I'd say yes to both.
3. **Inventory-editor** GitHub repo was listed but has no zip in plugins.rar — what is it supposed to contribute here?
4. **Attribute Modifiers' sub-items**: your tree also has Items/Player/Entity as targets — my mapping above (Tooltips→Items, PA→Player, CEM→Entity) is my assumption; confirm or correct.

---

## 8. Phase 2 EXECUTED (2026-10-06) — resource-only merge DONE

Build: `dist/fomek-plugin-v3.0.0-phase2.zip` (1723 files) + rerunnable `merge.py` + SRC Gradle scaffold (`build.gradle`, `settings.gradle`, placeholder `FomekPlugin.java`).

**Merged: 763 block JSONs, 752 .ftl templates, 69 root triggers, 4 blockly JS, 1 api (FomekCore), datalists/features/variables, 6 languages.**
All 890 JSON/YAML files validate; every palette category has its `$`-definition; only `neoforge-1.21.1/` kept.

Palette (block counts): Actions 30 · Data 105 · Utils 62 · Math 165 · Files 67 · Attribute Modifiers 13 · Items 23 · Player 42 · Entity 76 · Chunk 14 · Dimensions 20 · Multiverse 8 · Time 30 · Perception 12 · Input 1 · Superpowers 59.
Main category `fomek` = hue 260 (purple dark), `$`-file + lang key both written.

### Dedup decisions (first-wins in load order; flip any in the per-block pass)
| Block | Kept | Dropped |
|---|---|---|
| is_player_flying | RedWires | Procedures Plus |
| entity_get_persistence | RedWires | Snails |
| entity_set_persistence | RedWires | Snails |
| entity_get_fall_distance | RedWires | Snails |

### Broken blocks dropped (no neoforge-1.21.1 template — would fail codegen)
- chunk_force_load (chunk-manager, no ftl anywhere)
- keybind_get_key_1165only (PP, forge-1.16.5-only)
- entityisflying (PE, dup of RedWires is_player_flying)
- gendergetgender (PE, fabric-only; no Snails equivalent exists)
- world_data_light_level (Snails, no ftl; working block_light/sky_light kept)

### types.yaml
datalist/datamap kept as FQCN (`net.minecraft.nbt.ListTag`/`CompoundTag`, FomekPlugin variant) over MathUtils' short names — compiles without imports.

### Kept as-is (flagged for per-block pass)
- Snails procedure_labels (display-only palette headers, toolbox → Data)
- redwires global_trigger_procedures → Data, proceduresplusshapes → Math, PE client → Player, PE gender toolbox removed (was fabric-only)

### Next phases
3. FomekRenderer port (java + generator.yaml merge + 253 blocks) → also adds fomek_render category
4. FomekMenus port (java + engine templates + 161 blocks) → adds fomek_menus
5. Nerdy java plugins from GitHub 2026.1 (forge mixins, CEM, player animator, better animations) → adds fomek_animations; Inventory-editor scope still unclear (no zip in rar)

---

## 9. Phase 3 EXECUTED (2026-10-06) — FomekRenderer ported DONE

Build: `dist/fomek-plugin-v3.0.0-phase3.zip` (2,267 files). Now **1015 blocks, 992 .ftl, 72 triggers, 20 runtime templates**, merged generator.yaml (4 base_templates: 3 mixin classes + fomek.mixins.json, writer:json preserved).

**Palette structure for Render** (renderer's own subtree kept intact, hue 140):
```
Render ($fomek_render, parent fomek)
 ├─ BEWLR (Actions, Constructs, Data, Storage)
 ├─ Shapes
 ├─ Shader (GLSL, Shader Data, Templates*)
 └─ Data, Item, Overlay, World
```
*fomek_shader_templates was referenced by 5 shader blocks but had NO $-file and NO lang key in the source renderer (undefined category) — now properly defined under Shader as "Templates".

- Renderer blocks keep their machine names AND toolbox ids (no remap needed — only the top of the category chain was repointed: render_plugin → fomek_render → parent fomek). Existing workspaces survive untouched.
- types.yaml merged (7 renderer types: fomek_shape/model/shader/fragment/glsl/vec3/bewrl_part — no conflicts with datalist/datamap/dataelement/vector family).
- `fomek_rendertypes.yaml` kept as its own mapping file.
- Java: `FomekPlugin.java` (sole javaplugin entry, registers MCreatorLoadedEvent) delegates to `renderer/RendererRuntime.java` — 1:1 port of FomekRendererPlugin logic (api.render injection, stamp v1.18.1, mods.toml custom-mixins block injection). Real compile happens on the linked-MCreator gradle build (same as before).
- Runtime templates (templates/*.java: RenderAPI, BEWRL, Shader, etc.) copied verbatim, incl. the unused Fomek*-prefixed twins.

---

## 10. Phase 4 EXECUTED (2026-10-06) — FomekMenus ported DONE

Build: `dist/fomek-plugin-v3.0.0-phase4.zip` (2,608 files). Now **1175 blocks, 1144 .ftl, 74 triggers, 39 runtime templates** (20 renderer + 5 book/menus + 14 engine).

**Palette structure for Menus (REWORK)** ($fomek_menus generated: parent fomek, hue 160, label "Menus (REWORK)"; source children kept verbatim):
```
Menus (REWORK)
 ├─ Building Blocks (13)  Data (36)  Menu Data (19)  Menu Objects (12)
 ├─ Book (27)  Render (7)  Interactions (9)  Actions (29: "Triggers and Conditions" label from menus)
```
- Menus blocks keep machine names AND toolbox ids; only the top category was regenerated. $fomek_menus source file dropped in favor of ours.
- The menus subtree keeps its own per-subcategory labels/colors (incl. quirky "Triggers and Conditions" under fomek_menu_actions and color "0" — left verbatim for the REWORK pass).
- templates/: Book.java, BookPage.java, PageFlip.java + engine/ (Box, GuiState, GuiStatePayload, InputManager, MenuData, MenuEventHandler, MenuObject, MenuRenderHelper, PanelAttribute, PanelType, ScrollEventHandler, UpdateManager, VirtualGui, VirtualGuiElement) — copied recursively, no clashes with renderer's templates.
- Java: `menus/MenusRuntime.java` — 1:1 port of FomekMenusPlugin (runtime v1.9.26.1, stamp `.fomekmenus_runtime_stamp`, per-file presence verification, injection report file, legacy net.tamashi.fomekcore cleanup). FomekPlugin now registers it for BOTH MCreatorLoadedEvent and WorkspaceBuildStartedEvent (the menus plugin's own double-registration — critical because a plugin swap without MCreator restart would otherwise leave generated code importing a missing package).
- Root variables (fomek_menudata.json, fomek_menuobject.json) + generator variables YAMLs (fomek_menudata, fomek_menuobject) merged; the YAMLs contain FreeMarker `${fomek}` expressions — verbatim copies, intentionally NOT strict-YAML-parseable (validation false positive, noted).

### Next
Phase 5: Nerdy java plugins from GitHub 2026.1 — clone forge mixins (Mixin mod element), CEM (AnimatedModel/HumanoidModel elements, datalists, 38 blocks → Render/Entity), player animator (workspace panel + blocks → Animations), better animations (2 blocks → Animations). Also resolve Inventory-editor scope.

---

## 11. Phase 5 EXECUTED (2026-10-06) — Nerdy java plugins + Better Animations DONE (merge COMPLETE)

Build: `dist/fomek-plugin-v3.0.0-phase5-full.zip` (2,753 files). Final: **1,216 blocks, 1,178 .ftl, 79 triggers, 27 java classes**, 39 runtime templates. All JSON/YAML validated (except the two FreeMarker `${}` menus variable YAMLs — known false positives), every palette id has a $file, brace check clean.

### Ported subsystems (all sources: GitHub 2026.1 branches, cloned to `extracted/`)
- **Forge mixins** — `mixin` mod element (Mixin + MixinGUI) + dynamic `@modid.mixins.json` template (biome NoiseGeneratorSettingsMixin + per-element `mixins` list, `_merge_policy: override_all`).
- **Nerdy's CEM** — `humanoidmodel` + `animatedmodel` mod elements (AnimatedModel/HumanoidModel + GUIs), modelbridge js, field_model_selector/field_texture_selector blockly js, 26 blocks in CEM palette subtree (cemmodels 6, cemplayers 10, cemhitbox 2, cementities 2, cemarm 3, cemtextures 3), 5 triggers (after_model_registration, calculate_hitbox, render_arm/entity/player), datalists+mappings (modelparts, humanoidparts, animatedmodels, humanoidmodels, rendertypes; mappings/types.yaml merged: texturelocation/model/entityrendering/playerrendering/armrendering/hitboxcalculating/posestack). $nerdyscem reparented under **fomek_render** ("Nerdy's CEM" label via lang), children keep parent nerdyscem + own colors (hex strings, kept verbatim).
- **Player Animator** — workspace panel (WorkspacePanelPlayerAnimations), animbridge js, 7 blocks → new **fomek_animations** category (Animations, hue 300): nerdyplayeranimator 4 + the stray `advanced`-toolbox play_animation_advanced + better_animations 3. PA first-person mixin classes (camera_accessor, entity_render_dispatcher, item_in_hand_renderer/layer, level_renderer, living_entity_renderer, player_animation_api, player_model_mixin, player_renderer, animation_play_packet) merged.
- **Inventory editor** — `invedit` mod element (InventoryEdit + InventoryEditGUI + InveditGui editor + 8 dialog components), mutated_blocks.js, entity_open_inventory block → fomek_entity. inveditor Launcher deleted (FomekPlugin calls `PluginElementTypes.load()` directly).
- **Better Animations** — 3 blocks (set_entity_animation, set_entity_texture, set_item_animation) → fomek_animations; item/livingentity/item_renderer/livingentity_renderer template overrides; help pages. **Java: NO public source repo exists** (searched GitHub + web) → prebuilt `Launcher.class` from nerdys_better_animations.zip embedded at jar root (`resources/net/nerdypuzzle/betteranimations/Launcher.class`), attached reflectively from FomekPlugin (its constructor self-registers ModifyTemplateResultEvent + ModElementGUIEvent listeners). If it misbehaves: decompile or ask Nerdy for source.

### Decisions & conflicts resolved
- **modbase/mixin.json.ftl clash** (PA vs mixins plugin, differing content): PA's version is a strict superset (same dynamic mixins list + PA's 8 hardcoded client mixins) → PA wins (first-wins policy added to merge.py, `FILE_FIRST_WINS`), mixins plugin's copy dropped.
- Launcher classes kept their names/packages (`net.nerdypuzzle.*`, 25 classes total: forgemixins 3, entitymodels 6, playeranimator 3, inveditor 13) — converted from `extends JavaPlugin` to plain static-hook classes; parts referencing `Launcher.animations` untouched.
- FomekPlugin (only javaplugin entry) wires: PreGeneratorsLoadingEvent (mixin + humanoidmodel + animatedmodel + invedit), BlocklyPanelRegisterDOMData (modelbridge + animbridge), MCreatorLoadedEvent (RendererRuntime + MenusRuntime + PA panel), WorkspaceBuildStartedEvent (MenusRuntime), plus reflective Better Animations attach (try/catch with LOG.error).
- 2,735 files from 1.21.8/1.20.1 generators dropped per 1.21.1-only rule (incl. CEM's forge-1.20.1).

### Palette final counts
fomek: actions 30 · data 105 · utils 62 · math 165 · files 67 (21+24+11+11 subcats) · attrmods 13 · items 23 · player 42 · entity 77 · chunk 14 · dimensions 20 · multiverse 8 · time 30 · perception 12 · input 1 · superpowers 59 · animations 7 · render subtree 238 · CEM subtree 26 · menus subtree 152 · (1,151 visible + hidden).

### Remaining (user-side)
- Per-block filtering pass (REWORK labels in place), Gradle build against MCreator 2026.1 linked workspace, then real-workspace smoke test.
- NOTE for the user: five broken blocks were dropped as planned (no 1.21.1 template): chunk_force_load, keybind_get_key_1165only, entityisflying, gendergetgender, world_data_light_level.

## Phase 6 — Palette rework v3.1.0 (2026-10-06, user-specified)

### Removed blocks (10, machine names + reason)
- copy_block_properties, directly_set_itemstack_variable, entity_force_add_effect, entity_set_pickup_loot, get_object_from_forge_registry (user list)
- directly_get_itemstack (".copy()" remover twin of the above)
- is_keybind_pressed (client-only, duplicate of Input/Player keybind blocks)
- world_get_tick_rate (clashes with FomekCore time-perception API)
- entity_is_holding_item_with_tag / entity_is_holding_item_without_tag (user list)
All: json + 1.21.1 ftl + lang keys deleted; no cross-references in other blocks.

### New category tree (v3.3.0; root order via leading-space sort keys, see below)
Fomek
  Math · Files · Utils · Event Specific  (v3.2.4: Math+Files moved up to the top group,
     so no separator before Math anymore)
  NOTE v3.3.0: Superpowers, Multiverse, Time Travel, Perception and Dimensions are GONE —
  they moved to the separate non-java "Fomek Core" plugin (see SPLIT note below).
  ── Math (Vector, Vector list, Vector map, Utils, Random source, NBT, Number, Others, Data element, Data list, Data map — original MathUtils subcats restored 1:1 from plugins.rar)
  ── Files (Actions 21 / Data 24 / Utils 11 / Lists 11 — subcats restored 1:1 from
     the original file-manager plugin, incl. original order Utils before Lists via a
     one-space sort key on fomek_files_utils)
  ── World (Actions/Data; + the 4 shape-iteration blocks cube/hemisphere top+bottom/sphere
     moved in from Render/Shapes) · Player (Actions/Data/Animations) · Entity (Actions/Data/Specific/Attribute Modifiers) · Items (Actions/Data/Tooltips)
  ── Menus · Render (Shapes keeps the 5 vertex/create blocks) · Entity Models (was "Nerdy's CEM",
     moved OUT of Render to root child, id unchanged: nerdyscem) · Chunk
v3.2.3 removals: empty root fallback cats fomek_actions + fomek_data ($-jsons + lang keys gone).

### v3.3.0 SPLIT: "Fomek Core" plugin (non-java)
Everything FomekCore-API-dependent moved to a NEW plugin `fomekcore` ("Fomek Core", no java, no build):
  - 133 blocks: Superpowers 59, Time Travel 28, Perception 17, Dimensions 20, Multiverse 8,
    Core API 1 (input_is_key_down). Tick-rate blocks regrouped into Perception per user:
    tick_change/freeze_chunk (ex Chunk), tick_change/freeze_entity (ex Time Travel),
    world_set_tick_rate (ex World Actions). input_is_key_down → Core API.
  - 19 triggers: all sp_* (13) + timeline_branch_created, entity_multiversal_travel,
    multiverse_dimension_generated, process_multiverse_dimension_data, input_before_input, input_on_input.
  - 12 keybind_*.ftl templates (block jsons were removed earlier; kept for future blocks).
  - datalists/keybinds.yaml, blockly/js/fomek_blocks.js (biome/structure mutators, with a guarded
    copy of simpleRepeatingInputMixin so the core plugin works standalone).
  - apis/fomekcore_plugin.yaml (cursemaven fomek-core dependency) — CANONICAL copy now lives here.
  Root category "Fomek Core" with subcats Core API / Superpowers / Multiverse / Time Travel /
  Perception / Dimensions (sort-key padding 5..0).
REMOVED from FomekPlugin in v3.3.1: apis/fomekcore_plugin.yaml is now ONLY in the Fomek Core
plugin (the user's directive). The renderer no longer compile-time imports fomekcore:
new runtime template templates/FomekCoreCompat.java (injected into the render API package,
RendererRuntime CLASS_TEMPLATES + TEMPLATE_VERSION 1.18.2) talks to
net.tamashi.fomekcore.api.TickRateManager purely via reflection. So:
  - FomekCore NOT installed / "Fomek: Core" API unchecked -> mod compiles fine,
    slowmo calls degrade to vanilla partial ticks (bridge returns null / 20 TPS / false).
  - FomekCore plugin installed + API enabled -> jar on classpath, bridge binds once,
    slowmo rendering active. No hard link between the plugins at compile time.

- "(REWORK)" stripped from every label; Input category removed (its 1 block -> Player/Data);
  top-level Animations removed (4 blocks -> Player/Animations).
- 8 decorative procedure_label* blocks moved Data -> Utils (user request).
- Entity/Specific = creeper/enderman/spider/ghast/minecart/fireball(projectile)/neutral-anger blocks (29).
- Items/Tooltips = all tooltip* machine-name blocks (6); other items split Actions(9)/Data(18).
- Actions = do (statement shape), Data = get (output shape) — mechanical split applied to
  world/player/entity/items buckets; explicit overrides for cross-domain blocks.
- Event Specific = all "for global trigger" blocks + event result/setter family + command feedback (24).
- cube/sphere/hemisphere_* moved Math -> Shapes (they are shape loops, not math).
- clamp_number etc. moved Utils -> Math/Number; advancedbool/fixed_ternary stay Utils (logic utils).

### Colors (verified against mcreator-core block jsons)
Items 350 (red) · Entity 195 · Player 175 · World 35 (orange) · block blocks sit in World/Data
· Menus 110 (GUI green) · Dimensions 123 · Time Travel #628c94 (MCreator time) · Chunk 210
· Math 230 + all subcats · Files 30 · Actions/Data fallback 250 · Utils 205 · Event Specific 300
· Render subtree 140 · Attribute Modifiers 300 · Superpowers 290 · Multiverse 275 · Perception 320.
Menu subcategories keep their designed hues; CEM/render subtrees untouched.

### Root ordering hack (documented, important)
MCreator 2026.1 ExternalBlockLoader hardcodes `toolboxCategories.sort(byName)` — display order
is ALPHABETICAL by localized label and cannot be set via files. To deliver the user's exact
root order, the lang values for root children carry invisible leading spaces (rank 1 = 19
spaces ... rank 19 = 1 space). Space (32) < 'A' sorts earlier; HTML collapses them so labels
look identical. If labels are edited in lang files, keep the space counts (see Phase 5 note).

### Separators (CategorySeparators.java)
BREAK_BEFORE now: World, Menus, Superpowers (3 group starts; Math joined the top group in
v3.2.4; Attribute Modifiers
left this list in v3.2.3 — it moved into Entity, so its old break is gone).
Styling: thin divider LINE (1px rule, rgba(128,128,128,0.4)) — user prefers a visible line
over pure spacing (tried 6px spacer in v3.2.1, reverted in v3.2.2).
v3.2.1 bugfix: the \u0020 sort-key spaces broke matching — resolveLabel() passed the PADDED
label into the JS while the DOM text is compared trimmed, so no separator ever matched after
the ordering fix. Both sides are trimmed now (Java resolveLabel().trim() + JS textContent.trim()).

### Counts (1,138 visible blocks in 60 categories)
actions 0 · data 0 · utils 46 · event 24 · math 11 subcats 162 · files 67 (21+24+11+11 subcats) · world 11+42 ·
dimensions 20 · player 13+42+4 · entity 25+59+29 · items 9+18+6 · menus subtree 152 ·
render subtree 238 (+3 moved in) · chunk 14 · attrmods 13 · superpowers 59 · multiverse 8 ·
time travel 30 · perception 12 · CEM subtree 26.

### v3.3.2 block cleanup (user-requested removals)
Removed 9 blocks (json + ftl + lang keys): change_vanilla_texture, undo_texture,
drop_mainhand_full, drop_mainhand_one, drop_offhand_full, drop_offhand_one,
entity_open_inventory, world_data_is_player_within_range_of_entity,
world_data_is_player_within_range_of_pos.
"Are Overlays hidden" (overlay_hidden) moved from Render (root) into Render/Data.
fomek_render now contains ONLY child categories, no direct blocks.

### v3.3.3 block cleanup 2 (user-requested)
Removed 3 blocks: identity (Morph), identity_demorph (Remove morph),
spawn_egg_from_entity (Get spawn egg or AIR).
"For each entity in the provided world" (world_get_all_entities) moved from
Entity/Actions to World/Actions (fomek_world_actions).

### v3.4.0 Element Studio (popup blockly, MacroForge-style)
New blockly/js/fomekmenu_studio.js: each menu element block gets a blue "✎ edit"
pen field (runtime-injected; no json changes needed). Click opens a movable popup
window with its own Blockly workspace + per-kind flyout and a 1:1 canvas preview.
- Host blocks: Create button/slider/panel, Scroll view, Add item/rect/text/texture,
  Create book, Add page (book/page = framework only until the styles/events step).
- Option rows = the same fomekmenu_mutator_* blocks (now with VALUE sockets), edited
  inside the popup; value basics (text/number/boolean) still come from the main
  toolbox. The popup drives the host's existing decompose/compose machinery and
  round-trips value blocks into the host's hidden mutator inputs -> the generator
  reads the exact same XML as before, ZERO ftl changes.
- Gear bubble hidden on host blocks; options editable only in the studio.
- Interactions category deleted from the main toolbox: the 9 attr blocks + grid are
  popup-only (offered in the panel/scroll-view flyouts); loose drops onto the main
  canvas are quietly disposed by the escape guard.
- Migration: existing workspaces need nothing - old mutator inputs load as before,
  get hidden, and appear inside the pen popup on open.
- Book category: output blocks recoloured by data type (String/Boolean 210,
  Number 230, List 210) instead of all-book colour.

### v3.4.1 Element Studio fixes (feedback round 1)
- Popup workspace now runs with scrollbars:true (was false) — fixes the
  "block becomes not movable" bug: Blockly clamps top-level blocks back to
  fixed edges when scrollbars are disabled, which made option rows (Check/
  Stick/Collision) stick/freeze after being dragged inside the popup.
- Popup toolbox is now CATEGORIZED (<category name="Options">, <category
  name="Interactions">) instead of a flat flyout, so the sidebar tree shows
  up like the main editor's toolbox.
- Popup window is bigger by default (680-900 x 460-620, centered) and has a
  native CSS resize handle (bottom-right) wired to a ResizeObserver that
  calls Blockly.svgResize so the toolbox/flyout/canvas all reflow live.
- Trashcan enabled in the popup (quick way to remove an option row).
