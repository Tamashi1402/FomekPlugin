package net.tamashi.fomek.test;

import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.tamashi.fomek.test.FomekTestBoot.Issue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless plugin load test: boots MCreator 2026.2 with fomek-plugin.zip on
 * MCREATOR_PLUGINS_FOLDER, then verifies the javaplugin instantiated and the
 * merged neoforge-26.1.2 generator is usable. All load warnings are printed.
 *
 * Run with: gradlew pluginLoadTest
 */
public class PluginLoadTest {

	public static void main(String[] args) throws Exception {
		FomekTestBoot.boot();
		Logger LOG = LogManager.getLogger("Fomek load test");
		List<String> failures = new ArrayList<>();

		Plugin fomek = PluginLoader.INSTANCE.getPlugins().stream()
				.filter(p -> p.getID().equals("fomekplugin")).findFirst().orElse(null);
		if (fomek == null) {
			failures.add("fomekplugin was NOT loaded - MCREATOR_PLUGINS_FOLDER is wrong or the zip is missing");
		} else {
			LOG.info("fomekplugin plugin loaded: v{}", fomek.getInfo().getVersion());
			if (!fomek.isLoaded())
				failures.add("fomekplugin load failure: " + fomek.getLoadFailure());
			if (!net.tamashi.fomek.FomekPlugin.LOADED)
				failures.add("fomekplugin loaded but FomekPlugin constructor did not run (javaplugin not instantiated)");
		}
		for (var failed : PluginLoader.INSTANCE.getFailedPlugins())
			failures.add("failed plugin: " + failed.pluginID() + " - " + failed.message());

		GeneratorConfiguration fomekGen = Generator.GENERATOR_CACHE.get("neoforge-26.1.2");
		if (fomekGen == null)
			failures.add("neoforge-26.1.2 generator did not load");
		else
			LOG.info("neoforge-26.1.2 generator OK, status: {}", fomekGen.getGeneratorStats().getStatus());

		for (Issue issue : FomekTestBoot.ISSUES) {
			LOG.info("[{}] [{}] {}", issue.level(), issue.logger(), issue.message());
			if (issue.throwable() != null)
				issue.throwable().printStackTrace(System.out);
		}

		if (!failures.isEmpty()) {
			failures.forEach(f -> LOG.error("FAIL: {}", f));
			System.out.println("\n=== LOAD TEST: FAILED (" + failures.size() + " failure(s), "
					+ FomekTestBoot.ISSUES.size() + " warning(s)/error(s) during load) ===");
			System.exit(1);
		}

		System.out.println("\n=== LOAD TEST: PASSED with " + FomekTestBoot.ISSUES.size()
				+ " warning(s)/error(s) during load ===");
		System.exit(FomekTestBoot.ISSUES.isEmpty() ? 0 : 2);
	}
}
