package net.tamashi.fomek.test;

import net.mcreator.Launcher;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.ui.init.BlocklyJavaScriptsLoader;
import net.mcreator.ui.init.BlocklyToolboxesLoader;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.io.LoggingSystem;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.UTF8Forcer;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.mcreator.ui.init.EntityAnimationsLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Headless plugin load test: emulates MCreator 2026.2 startup (the parts that
 * matter for plugins) with fomek-plugin.zip on MCREATOR_PLUGINS_FOLDER, then
 * reports every WARN/ERROR emitted while the plugin loads, and validates that
 * the neoforge-26.1.2 generator merges + all blockly/ME types load.
 *
 * Run with: gradlew pluginLoadTest
 */
public class PluginLoadTest {

	public record Issue(String level, String logger, String message, Throwable throwable) {}

	private static final List<Issue> ISSUES = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		LoggingSystem.init();

		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		AbstractAppender collector = new AbstractAppender("LoadTestCollector", null, null, false,
				Property.EMPTY_ARRAY) {
			@Override public void append(LogEvent event) {
				if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
					ISSUES.add(new Issue(event.getLevel().toString(), event.getLoggerName(),
							event.getMessage().getFormattedMessage(), event.getThrown()));
				}
			}
		};
		collector.start();
		config.getRootLogger().addAppender(collector, Level.ALL, null);
		ctx.updateLoggers();

		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		UTF8Forcer.forceGlobalUTF8();
		System.setProperty("jdk.xml.maxElementDepth", "0");

		Logger LOG = LogManager.getLogger("Fomek load test");

		Properties conf = new Properties();
		conf.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
		Launcher.version = new MCreatorVersionNumber(conf);
		LOG.info("MCreator version under test: {}", Launcher.version);

		PreferencesManager.init();
		// hidden pref: Java plugins must be enabled for the javaplugin entry to load
		PreferencesManager.PREFERENCES.hidden.enableJavaPlugins.set(true);

		// no analytics, no telemetry for the load test
		net.mcreator.io.net.analytics.GoogleAnalytics.ANALYTICS_ENABLED = false;
		MCreatorApplication.isInternet = false;

		LOG.info("Loading plugins (fomek-plugin.zip via MCREATOR_PLUGINS_FOLDER)...");
		PluginLoader.initInstance();

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

		// UI theme + images: MCItem's class initializer needs UIRES icons
		net.mcreator.ui.laf.themes.ThemeManager.loadThemes();
		net.mcreator.ui.init.UIRES.preloadImages();
		try {
			net.mcreator.ui.laf.themes.ThemeManager.applySelectedTheme();
		} catch (Throwable t) {
			LOG.info("applySelectedTheme skipped headless: {}", t.toString());
		}

		DataListLoader.preloadCache();
		net.mcreator.ui.init.L10N.initTranslations();
		ModAPIManager.initAPIs();
		VariableTypeLoader.loadVariableTypes();
		BlocklyJavaScriptsLoader.init();
		BlocklyToolboxesLoader.init();
		BlocklyLoader.init();
		EntityAnimationsLoader.init();
		ModElementTypeLoader.loadModElements();

		Set<String> fileNames = PluginLoader.INSTANCE.getResources(Pattern.compile("generator\\.yaml"));
		for (String generator : fileNames) {
			generator = generator.replace("/generator.yaml", "");
			LOG.info("Loading generator: {}", generator);
			Generator.GENERATOR_CACHE.put(generator, new GeneratorConfiguration(generator));
		}

		// ── Validation ─────────────────────────────────────────────────────
		GeneratorConfiguration fomekGen = Generator.GENERATOR_CACHE.get("neoforge-26.1.2");
		if (fomekGen == null)
			failures.add("neoforge-26.1.2 generator did not load");
		else
			LOG.info("neoforge-26.1.2 generator OK, status: {}", fomekGen.getGeneratorStats().getStatus());

		for (Issue issue : ISSUES) {
			LOG.info("[{}] [{}] {}", issue.level(), issue.logger(), issue.message());
			if (issue.throwable() != null)
				issue.throwable().printStackTrace(System.out);
		}

		if (!failures.isEmpty()) {
			failures.forEach(f -> LOG.error("FAIL: {}", f));
			System.out.println("\n=== LOAD TEST: FAILED (" + failures.size() + " failure(s), " + ISSUES.size()
					+ " warning(s)/error(s) during load) ===");
			System.exit(1);
		}

		System.out.println("\n=== LOAD TEST: PASSED with " + ISSUES.size() + " warning(s)/error(s) during load ===");
		System.exit(ISSUES.isEmpty() ? 0 : 2); // exit 2 = loaded but with warnings, still report them
	}
}
