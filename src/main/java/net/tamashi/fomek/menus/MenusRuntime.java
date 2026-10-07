package net.tamashi.fomek.menus;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * MenusRuntime — ported from FomekMenusPlugin (phase 4).
 *
 * Injects the full FomekMenus runtime (engine + book system) into
 * <modpackage>.api.guisystems, with stamp-controlled re-injection
 * (.fomekmenus_runtime_stamp, RUNTIME_VERSION), per-file presence
 * verification, a human-readable injection report in the workspace, and
 * legacy fixed-package (net.tamashi.fomekcore) cleanup.
 *
 * Registered for BOTH MCreatorLoadedEvent and WorkspaceBuildStartedEvent:
 * the loaded event fires exactly once per app launch, so a plugin swap
 * without restarting would otherwise leave a stale/missing runtime while
 * generated code still imports it -> re-run injection before every build.
 */
public class MenusRuntime {

    private static final Logger LOG = LogManager.getLogger("Fomek Menus");

    /** Bump to force re-injection of the full runtime into existing workspaces. */
    private static final String RUNTIME_VERSION = "1.11.0-studio";

    private static final String RUNTIME_SUFFIX = ".api.guisystems";
    private static final String LEGACY_DIR = "net/tamashi/fomekcore";
    private static final String STAMP_FILE = ".fomekmenus_runtime_stamp";

    /** The book system classes (this plugin's own code). */
    private static final String[] TEMPLATES = {"/templates/PageFlip.java", "/templates/Book.java", "/templates/BookPage.java"};

    /** The FomekMenus engine (bundled with the plugin, injected into every workspace). */
    private static final String[] ENGINE_TEMPLATES = {
            "/templates/engine/Box.java", "/templates/engine/GuiState.java", "/templates/engine/GuiStatePayload.java",
            "/templates/engine/InputManager.java", "/templates/engine/MenuData.java", "/templates/engine/MenuEventHandler.java",
            "/templates/engine/MenuObject.java", "/templates/engine/MenuRenderHelper.java", "/templates/engine/PanelAttribute.java",
            "/templates/engine/PanelType.java", "/templates/engine/ScrollEventHandler.java", "/templates/engine/UpdateManager.java",
            "/templates/engine/StudioRuntime.java", "/templates/engine/MenuControls.java",
            "/templates/engine/MenuStyle.java", "/templates/engine/MenuText.java", "/templates/engine/MenuTextInput.java",
            "/templates/engine/VirtualGui.java", "/templates/engine/VirtualGuiElement.java"};

    
    /** Called by FomekPlugin on MCreatorLoadedEvent. */
    public void onMCreatorLoaded(MCreator mcreator) {
        if (mcreator.getWorkspace() != null) run(mcreator.getWorkspace());
    }

    /** Called by FomekPlugin on WorkspaceBuildStartedEvent (see class docs). */
    public void onBuildStarted(MCreator mcreator) {
        if (mcreator.getWorkspace() != null) run(mcreator.getWorkspace());
    }

    private void run(Workspace workspace) {
        injectRuntime(workspace);
        cleanupLegacyRuntime(workspace);
    }


    /** Headless build support: same injection as the UI events, callable from tests/CI. */
    public void injectForBuild(Workspace workspace) { injectRuntime(workspace); }

    private void injectRuntime(Workspace workspace) {
        try {
            String modPackage = getModPackage(workspace);
            if (modPackage == null || modPackage.isEmpty()) {
                LOG.warn("Could not determine mod package, skipping FomekMenus runtime injection");
                writeReport(workspace, "FAILED: could not determine mod package (getModElementsPackage() and "
                        + "every fallback returned nothing). Runtime was NOT injected -- generated code that "
                        + "references <package>.api.guisystems will fail to compile until this is fixed.");
                return;
            }

            String modId = getModId(workspace);
            if (modId == null || modId.isEmpty()) {
                // Fallback: derive modid from last package segment
                modId = modPackage.substring(modPackage.lastIndexOf('.') + 1);
                LOG.warn("Could not determine mod ID, derived from package: {}", modId);
            }

            String runtimePackage = modPackage + RUNTIME_SUFFIX;
            String packagePath = runtimePackage.replace('.', '/');
            String sourceRoot = getWorkspaceSourceRoot(workspace);
            String targetDir = sourceRoot + "/" + packagePath;

            LOG.info("Injecting FomekMenus runtime into: {} (package: {}, modid: {})", targetDir, runtimePackage, modId);

            File stampFile = new File(targetDir, STAMP_FILE);
            if (stampFile.exists()) {
                String existingVersion = new String(Files.readAllBytes(stampFile.toPath())).trim();
                if (RUNTIME_VERSION.equals(existingVersion) && allRuntimeFilesPresent(targetDir)) {
                    LOG.info("FomekMenus runtime already up-to-date (v{}), skipping", RUNTIME_VERSION);
                    writeReport(workspace, "OK (already up-to-date): runtime v" + RUNTIME_VERSION + " present at "
                            + targetDir + " (package " + runtimePackage + ", modid " + modId + ")");
                    return;
                }
            }

            new File(targetDir).mkdirs();

            String[] all = new String[TEMPLATES.length + ENGINE_TEMPLATES.length];
            System.arraycopy(TEMPLATES, 0, all, 0, TEMPLATES.length);
            System.arraycopy(ENGINE_TEMPLATES, 0, all, TEMPLATES.length, ENGINE_TEMPLATES.length);

            for (String template : all) {
                injectTemplate(template, targetDir, runtimePackage, modId);
            }

            Files.write(stampFile.toPath(), RUNTIME_VERSION.getBytes());

            LOG.info("Injected {} FomekMenus runtime classes into {}", all.length, runtimePackage);
            writeReport(workspace, "OK: injected " + all.length + " runtime classes (v" + RUNTIME_VERSION + ") into "
                    + targetDir + " (package " + runtimePackage + ", modid " + modId + ")");
        } catch (Exception e) {
            LOG.error("Failed to inject FomekMenus runtime", e);
            writeReport(workspace, "FAILED with exception: " + e);
        }
    }

    /**
     * Visible, in-workspace diagnostic (NOT just the MCreator app log, which
     * most people never open). Overwritten on every MCreator load and every
     * build, so after a build failure you can open this file and immediately
     * see whether the runtime was actually injected, and into which package
     * -- instead of guessing from a "package ... does not exist" compiler
     * error whose cause (missing injection vs. stale generated code vs.
     * needing a MCreator restart) isn't obvious from the error alone.
     */
    private void writeReport(Workspace workspace, String status) {
        try {
            File workspaceDir = workspace.getWorkspaceFolder();
            File report = new File(workspaceDir, STAMP_FILE.replace(".fomekmenus_runtime_stamp", "fomekmenus_injection_report.txt"));
            String content = "FomekMenus plugin v" + RUNTIME_VERSION + " - " + new java.util.Date() + "\n" + status
                    + "\n\nIf this says FAILED, or if a build still complains about a missing package after this "
                    + "says OK: fully restart MCreator (plugins only load once per app launch), then use the "
                    + "Workspace menu's Regenerate Code action, then build again.\n";
            Files.writeString(report.toPath(), content);
        } catch (Exception ignored) {
            // Diagnostics must never break injection itself.
        }
    }

    /**
     * A matching stamp alone is not trusted: every runtime class file must
     * actually exist, otherwise a failed injection (e.g. templates missing
     * from an older plugin build) would be skipped forever.
     */
    private boolean allRuntimeFilesPresent(String targetDir) {
        String[] all = new String[TEMPLATES.length + ENGINE_TEMPLATES.length];
        System.arraycopy(TEMPLATES, 0, all, 0, TEMPLATES.length);
        System.arraycopy(ENGINE_TEMPLATES, 0, all, TEMPLATES.length, ENGINE_TEMPLATES.length);
        for (String template : all) {
            String fileName = template.substring(template.lastIndexOf('/') + 1);
            if (!new File(targetDir, fileName).exists())
                return false;
        }
        return true;
    }

    private void injectTemplate(String templatePath, String targetDir, String runtimePackage, String modId)
            throws IOException {
        // ClassLoader resource names must NOT start with a slash
        String resourceName = templatePath.startsWith("/") ? templatePath.substring(1) : templatePath;
        InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IOException("Runtime template not found inside plugin jar: " + templatePath);
        }

        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        in.close();

        // rewrite the template into the workspace's own package
        content = content.replace("package net.tamashi.fomekcore.api.guisystems;",
                "package " + runtimePackage + ";");
        // engine payload namespace follows the mod id
        content = content.replace("@FOMEK_MODID@", modId);

        String fileName = templatePath.substring(templatePath.lastIndexOf('/') + 1);
        File outputFile = new File(targetDir, fileName);
        Files.writeString(outputFile.toPath(), content);

        LOG.debug("Injected: {}", outputFile.getPath());
    }

    /**
     * Delete a runtime folder this plugin injected into the old fixed
     * package (net.tamashi.fomekcore) in a previous version. Only touches
     * it when OUR stamp file proves we created it — a hand-made engine
     * copy is never deleted.
     */
    private void cleanupLegacyRuntime(Workspace workspace) {
        try {
            String sourceRoot = getWorkspaceSourceRoot(workspace);
            File legacyRoot = new File(sourceRoot, LEGACY_DIR);
            if (!legacyRoot.exists()) return;
            File legacyStamp = new File(legacyRoot, "api/guisystems/" + STAMP_FILE);
            if (legacyStamp.exists()) {
                deleteRecursively(legacyRoot);
                LOG.info("Removed legacy FomekMenus runtime folder (injected by an old plugin version): {}",
                        legacyRoot.getAbsolutePath());
            } else {
                LOG.warn("Legacy folder {} exists but was not injected by this plugin - leaving it untouched.",
                        legacyRoot.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.error("Legacy FomekMenus runtime cleanup failed", e);
        }
    }

    private static void deleteRecursively(File root) throws Exception {
        try (var stream = Files.walk(root.toPath())) {
            stream.sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
        }
    }

    private String getModPackage(Workspace workspace) {
        try {
            Object settings = workspace.getWorkspaceSettings();

            try {
                java.lang.reflect.Method m = settings.getClass().getMethod("getModElementsPackage");
                Object result = m.invoke(settings);
                if (result != null && !result.toString().isBlank()) {
                    return result.toString();
                }
            } catch (NoSuchMethodException ignored) {
            }

            for (String methodName : new String[]{
                    "getJavaPackagePath", "getPackageName", "getModPackageName"
            }) {
                try {
                    java.lang.reflect.Method m = settings.getClass().getMethod(methodName);
                    Object result = m.invoke(settings);
                    if (result != null && !result.toString().isBlank()) {
                        return result.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }

            String sourceRoot = getWorkspaceSourceRoot(workspace);
            File srcDir = new File(sourceRoot);
            if (srcDir.exists()) {
                String found = findPackageFromSource(srcDir);
                if (found != null) return found;
            }

        } catch (Exception e) {
            LOG.error("Failed to get mod package", e);
        }
        return null;
    }

    private String getModId(Workspace workspace) {
        try {
            Object settings = workspace.getWorkspaceSettings();

            for (String methodName : new String[]{"getModID", "getModId", "getModid"}) {
                try {
                    java.lang.reflect.Method m = settings.getClass().getMethod(methodName);
                    Object result = m.invoke(settings);
                    if (result != null && !result.toString().isBlank()) {
                        return result.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to get mod ID", e);
        }
        return null;
    }

    /**
     * Last-resort fallback when getModElementsPackage() (and its reflective
     * aliases) are unavailable. Scans every .java file under the source
     * root and returns the SHORTEST package found (fewest dot segments),
     * not just the first file encountered.
     *
     * This matters: a naive "first file wins" scan is directory-order
     * dependent and will often land on a .java file sitting in a
     * sub-package (e.g. ".../deathnote/procedures/Foo.java", package
     * "net.tamashi.deathnote.procedures") rather than the mod's actual
     * root package ("net.tamashi.deathnote") -- the exact kind of
     * one-level-off mismatch that produces a runtime injected at
     * "...deathnote.procedures.api.guisystems" while every generated
     * import still expects "...deathnote.api.guisystems". The shortest
     * package among all source files is always the best guess at the
     * true root, since every sub-package (procedures, mixin, api, ...)
     * is strictly longer than it.
     */
    private String findPackageFromSource(File dir) {
        String best = null;
        for (String pkg : collectPackages(dir)) {
            if (best == null || pkg.length() < best.length()) best = pkg;
        }
        return best;
    }

    private java.util.List<String> collectPackages(File dir) {
        java.util.List<String> found = new java.util.ArrayList<>();
        collectPackages(dir, found);
        return found;
    }

    private void collectPackages(File dir, java.util.List<String> found) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                // never let the legacy fixed-package folder win the scan
                if (f.getAbsolutePath().replace('\\', '/').contains("/net/tamashi/fomekcore")) continue;
                collectPackages(f, found);
            } else if (f.getName().endsWith(".java")) {
                try {
                    for (String line : Files.readAllLines(f.toPath())) {
                        if (line.startsWith("package ")) {
                            found.add(line.replace("package ", "").replace(";", "").trim());
                            break;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String getWorkspaceSourceRoot(Workspace workspace) {
        File workspaceDir = workspace.getWorkspaceFolder();
        return workspaceDir.getAbsolutePath() + "/src/main/java";
    }
}
