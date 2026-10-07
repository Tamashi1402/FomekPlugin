package net.tamashi.fomek.test;

import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.OutputStreamEventHandler;
import net.mcreator.workspace.Workspace;
import net.tamashi.fomek.FomekPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;

import java.io.File;
import java.nio.file.Files;

/**
 * Headless workspace build test: creates a neoforge-26.1.2 workspace, injects
 * the fomek runtime exactly like MCreatorLoadedEvent would, then runs the full
 * Gradle "build" task and prints every javac error — so template/API port work
 * can be verified without opening MCreator.
 *
 * Run with: gradlew pluginWorkspaceTest [-PfomekWsElements=true]
 */
public class WorkspaceBuildTest {

	public static void main(String[] args) throws Exception {
		Logger LOG = LogManager.getLogger("Fomek workspace build test");

		FomekTestBoot.boot();

		if (FomekTestBoot.ISSUES.stream().anyMatch(i -> i.level().equals("ERROR")))
			LOG.warn("Boot completed with {} ERROR-level issue(s) — continuing", FomekTestBoot.ISSUES.size());

		File workspaceDir = new File(System.getenv("FOMEK_TEST_DIR"), "build/workspace-test");
		Workspace workspace = FomekTestBoot.createWorkspace(workspaceDir, "neoforge-26.1.2");
		LOG.info("Workspace created at {}", workspaceDir);

		// inject fomek runtime (renderer + menus), same as MCreatorLoadedEvent path
		FomekPlugin.injectRuntimeHeadless(workspace);
		LOG.info("Fomek runtime injected");

		// log what got injected
		try (var stream = Files.walk(workspaceDir.toPath().resolve("src/main/java"))) {
			stream.filter(p -> p.toString().endsWith(".java"))
					.forEach(p -> LOG.info("workspace source: {}", workspaceDir.toPath().relativize(p)));
		}

		LOG.info("Starting Gradle build (first run downloads NeoForge 26.1 — this takes a while)...");
		StringBuilder sb = new StringBuilder();
		BuildLauncher buildLauncher = GradleUtils.getGradleTaskLauncher(
				workspace.getGeneratorConfiguration(), GradleUtils.getGradleProjectConnection(workspace), "build");
		buildLauncher.setStandardError(new OutputStreamEventHandler(line -> sb.append(line)
				.append(System.lineSeparator())));

		try {
			buildLauncher.run();
			System.out.println("\n=== WORKSPACE BUILD: OK ===");
			if (sb.toString().contains(": warning:") || sb.toString().contains(": error:")) {
				LOG.warn("Build had warnings/errors in log:\n{}", sb);
				System.out.println("\n=== WORKSPACE BUILD: OK, but with javac output (see log) ===");
				System.exit(2);
			}
			System.exit(0);
		} catch (GradleConnectionException | IllegalStateException e) {
			Files.writeString(new File(System.getenv("FOMEK_TEST_DIR"), "build/workspace-test-build.log").toPath(),
					sb.toString());
			LOG.error("Gradle build FAILED — full javac output in build/workspace-test-build.log:\n{}", sb, e);
			System.out.println("\n=== WORKSPACE BUILD: FAILED (see build/workspace-test-build.log) ===");
			System.exit(1);
		}
	}
}
