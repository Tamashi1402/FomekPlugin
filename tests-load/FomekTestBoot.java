package net.tamashi.fomek.test;

import net.mcreator.Launcher;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.io.LoggingSystem;
import net.mcreator.minecraft.DataListLoader;
import net.mcreator.ui.init.EntityAnimationsLoader;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.init.BlocklyJavaScriptsLoader;
import net.mcreator.ui.init.BlocklyToolboxesLoader;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.UTF8Forcer;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.VariableTypeLoader;
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
 * Shared headless boot for all fomek test harnesses: brings up MCreator 2026.2
 * far enough that plugins load, blockly/me types/generators are registered —
 * no GUI, no display needed.
 */
public class FomekTestBoot {

	public record Issue(String level, String logger, String message, Throwable throwable) {}

	public static final List<Issue> ISSUES = new ArrayList<>();

	public static void boot() throws Exception {
		LoggingSystem.init();

		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		AbstractAppender collector = new AbstractAppender("FomekTestCollector", null, null, false,
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

		Logger LOG = LogManager.getLogger("Fomek boot");

		Properties conf = new Properties();
		conf.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
		Launcher.version = new MCreatorVersionNumber(conf);
		LOG.info("MCreator version under test: {}", Launcher.version);

		PreferencesManager.init();
		PreferencesManager.PREFERENCES.hidden.enableJavaPlugins.set(true);
		// gradle builds of test workspaces need RAM and a known JDK
		PreferencesManager.PREFERENCES.gradle.xmx.set(
				net.mcreator.preferences.data.GradleSection.MAX_RAM);
		net.mcreator.io.net.analytics.GoogleAnalytics.ANALYTICS_ENABLED = false;
		MCreatorApplication.isInternet = false;

		LOG.info("Loading plugins (fomek-plugin.zip via MCREATOR_PLUGINS_FOLDER)...");
		PluginLoader.initInstance();

		ThemeManager.loadThemes();
		UIRES.preloadImages();
		try {
			ThemeManager.applySelectedTheme();
		} catch (Throwable t) {
			LOG.info("applySelectedTheme skipped headless: {}", t.toString());
		}

		DataListLoader.preloadCache();
		L10N.initTranslations();
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
	}

	public static GeneratorConfiguration requireGenerator(String name) {
		GeneratorConfiguration config = Generator.GENERATOR_CACHE.get(name);
		if (config == null)
			throw new IllegalStateException("Generator not loaded: " + name);
		return config;
	}

	/** Creates + base-generates a test workspace in the given directory (wiped first). */
	public static Workspace createWorkspace(java.io.File workspaceDir, String generatorName)
			throws Exception {
		org.apache.commons.io.FileUtils.deleteDirectory(workspaceDir);
		workspaceDir.mkdirs();
		Workspace workspace = net.mcreator.integration.TestWorkspaceDataProvider.createTestWorkspace(
				workspaceDir, requireGenerator(generatorName), true, false, new java.util.Random(1234));
		WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
		return workspace;
	}
}
