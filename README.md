# FomekPlugin (nf-26.1.2 port)

All-in-one MCreator 2026.2 plugin (NeoForge 26.1.x generator). Port of the
nf-1.21.1 branch (MCreator 2026.1, NeoForge 1.21.1).

## Build
1. Extract the MCreator 2026.2 SOURCE zip and run `gradlew downloadJDKWin64`
   inside it once (provides the Java 25 + JCEF toolchain).
2. Set `mcreator_path` and `org.gradle.java.home` in `gradle.properties`
   (see gradle.properties.example).
3. `gradlew jar` -> build/libs/fomek-plugin.zip
4. Drop it into `<MCreator>/plugins/`, or run `gradlew runMCreatorWithPlugin`.

Java 25 toolchain (Gradle 9.6, required by MCreator 2026.2).

Port status and remaining work: see PORT-PROGRESS.md.
Menu editor changes and usage: see MENU_STUDIO.txt. Regression checks: tests/.
