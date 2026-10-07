# FomekPlugin v3.6.0

All-in-one MCreator 2026.1 plugin (NeoForge 1.21.1 only). Merge of 16 plugins:
FomekPlugin, FomekMenus, FomekRenderer, forge mixins, Nerdy's CEM, Player
Animator, Inventory editor, MathUtils, file manager, Item tooltips, attribute
modifiers, chunk manager, redwires, Minos Procedures Plus, ProceduresExtras,
Snails (Better Animations stripped).

## Build
1. Set `mcreator_path` in `gradle.properties` to your extracted MCreator source.
2. `gradlew jar` -> build/libs/fomek-plugin.zip
3. Drop it into `<MCreator>/plugins/`, or run `gradlew runMCreatorWithPlugin`
   to launch MCreator with the plugin preloaded.

Java 21 toolchain (use the JDK bundled with MCreator via org.gradle.java.home).

Menu editor changes and usage: see MENU_STUDIO.txt. Regression checks are in tests/.

