# FomekPlugin port: 2026.1 → 2026.2 (nf-26.1.2 branch)

**This file is the hand-off document.** Update it at the end of every work chunk
and commit it together with the chunk. The next agent (or the next session)
reads this first. GitHub pushes are currently broken (see "Known issues"), so
local commits + the bundle backup are the source of truth.

## Goal

Port FomekPlugin (MCreator plugin) from MCreator 2026.1 / generator
`neoforge-1.21.1` to MCreator **2026.2** / generator **`neoforge-26.1.2`**
(NeoForge 26.1.x for Minecraft 26.1), so the plugin compiles, loads and its
generated workspaces build against the new APIs.

Source history: branch `nf-1.21.1` was copied into branch `nf-26.1.2`.
Strategy per owner: work piece by piece, commit each chunk, verify compile
errors headlessly so the owner does not have to run MCreator himself.

## Repo layout (what is what)

- `src/main/resources/plugin.json` — `supportedversions` bumped to 2026002.
- `src/main/resources/neoforge-26.1.2/` — the fomek generator (renamed from
  `neoforge-1.21.1/`; `generator.yaml` status/check relevant keys updated).
- `src/main/resources/templates/` — runtime templates injected into user
  workspaces (`RenderAPI.java`, `FomekRenderAPI.java`, `VirtualGui.java`,
  engine/…). These are the BIGGEST port surface: they compile against raw
  Minecraft/NeoForge 26.1 APIs inside the generated workspace.
- `src/main/java/net/tamashi/fomek/` — plugin Java code. Builds OK against
  MCreator 2026.2 APIs.
- `tests-load/` — headless harness (see below).
- MCreator 2026.2 sources live OUTSIDE the repo at the path in
  `gradle.properties` (`mcreator_path`), plus JDK 25 (JBR with JCEF) under
  `mcreator-src/jdk/jbr25_linux_64`. `gradle.properties` is gitignored.

## Done so far (in commit order)

1. **Foundation** (26c5501): plugin compiles under MCreator 2026.2 (Gradle 9.6,
   Java 25). Fixed the Gradle-9 `afterEvaluate`/ProjectDependency crash the
   owner hit on Windows. Generator folder renamed `neoforge-1.21.1` →
   `neoforge-26.1.2`, `supportedversions: 2026002`, jar-detection + unit test
   updated.
2. **Headless load test** (1c48af6): `gradlew pluginLoadTest` boots MCreator
   2026.2 with `fomek-plugin.zip` via `MCREATOR_PLUGINS_FOLDER`, asserts
   FomekPlugin instantiated and `neoforge-26.1.2` generator usable. Result:
   **loads clean**. Only issues: ~274 missing en_US translations (fomek
   blockly categories/blocks — cosmetic, same on 2026.1) + one pre-existing
   warning: generator `variables/fomek_animator.yaml` has no matching
   `resources/variables/fomek_animator.json` type declaration (GeneratorVariableTypes
   skips it). Pre-existing on the old branch, NOT a port regression; decide
   later whether to add the json or drop the yaml.
   Also: `.gitignore` added; build/ .gradle/ artifacts untracked.
3. **Headless workspace-build harness** (db5d31c): `gradlew pluginWorkspaceTest`:
   creates a `neoforge-26.1.2` test workspace (MCreator's own
   `TestWorkspaceDataProvider.createTestWorkspace`), injects the fomek runtime
   (`FomekPlugin.injectRuntimeHeadless` = renderer + menus injection, same
   code path as MCreatorLoadedEvent), then runs the full Gradle `build` of the
   workspace and dumps every javac error to `build/workspace-test-build.log`.
   This is the compile-error surfacing loop for the template port.
   Test classpath wiring (build.gradle): MCreator test classes attached as
   file collections (`mcreator-src/build/classes/java/test`), since :MCreator
   exposes no consumable test variant; MCreator's implementation deps are
   copied into our implementation config via afterEvaluate (Gradle 9: filter
   ProjectDependency, else duplicate project dep).

## In progress

- First `pluginWorkspaceTest` run (downloads NeoForge 26.1 toolchain on first
  run). Expect the injected runtime templates (`RenderAPI.java`,
  `VirtualGui.java`, …) to produce compile errors against 26.1 APIs — those
  errors are the work queue for the next chunks.

## Next steps (ordered)

1. Collect `build/workspace-test-build.log` javac errors, fix the injected
   runtime templates chunk by chunk (commit per subsystem: RenderAPI,
   VirtualGui, engine/, menus).
2. Extend WorkspaceBuildTest to also generate fomek mod elements / procedures
   using every fomek blockly block (like MCreator's GTProcedureBlocks) so the
   ~1033 procedure ftl templates get exercised too. Most fomek procedure
   blocks only call fomek's own runtime API (stable), but MC-API-touching ones
   must be ported.
3. FomekCore mod (owner-attached FomekCore-newest-net.zip): verify against
  NeoForge 26.1.2 once the workspace builds.
4. Re-check the fomek_animator variable type warning.
5. Push to GitHub when it works again; keep `git bundle create` backups +
   upload them (last bundle: base44 upload `a04afd9fc_fomek-backup.bundle`).

## Known issues / gotchas

- **GitHub push broken (server-side)**: any `git push` to Tamashi1402/FomekPlugin
  (even an empty commit, any branch) and the Contents API return HTTP 500
  "Internal Server Error"; reads work fine. Fresh connector token did not
  help. Owner should check the repo on github.com / support. Until fixed:
  local commits + `git bundle create ../fomek-backup.bundle --all` after each
  chunk, uploaded via Base44 public storage.
- MCreator 2026.2 builds with **Java 25** (JBR 25 with JCEF);
  `flatDir mcreator_path/lib` provides the bundled jars.
- `generator.yaml` of the plugin defines the merged generator; its status
  was left at whatever 2026.1 had unless compile demanded otherwise.
- 99% of the plugin's 1033 procedure templates are fomek-custom (only 3 match
  core MCreator blocks by name), so the "diff against core generator" strategy
  barely applies — port via workspace compile errors instead.
- Missing translations: all `blockly.category.fomek_*` / block keys for
  en_US; keys exist in other locales (texts_*.properties). Optional polish.
