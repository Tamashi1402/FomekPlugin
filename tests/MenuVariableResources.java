// Run with MCreator's snakeyaml-engine jar on the classpath and the plugin's
// src/main/resources directory as the argument. MCreator parses YAML before FTL.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

class MenuVariableResources {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass src/main/resources");
        var loader = new Load(LoadSettings.builder().build());
        for (var entry : Map.of("fomek_menuobject", "MenuObject", "fomek_menudata", "MenuData").entrySet()) {
            var file = Path.of(args[0], "neoforge-1.21.1", "variables", entry.getKey() + ".yaml");
            var source = Files.readString(file);
            var definition = (Map<?, ?>) loader.loadFromString(source);
            var type = "${package}.api.guisystems." + entry.getValue();
            if (!("new " + type + "()").equals(definition.get("defaultvalue")))
                throw new AssertionError(file + ": incorrect default constructor");
            var local = (Map<?, ?>) ((Map<?, ?>) definition.get("scopes")).get("local");
            if (!(type + " ${var.getName()} = new " + type + "();").equals(local.get("init")))
                throw new AssertionError(file + ": incorrect local initialization");
            if (!"${name}".equals(local.get("get")) || !"${name} = ${opt.removeParentheses(value)};".equals(local.get("set")))
                throw new AssertionError(file + ": incorrect get/set templates");
            System.out.println("PASS: " + entry.getKey() + " YAML loads before template evaluation");
        }
    }
}
