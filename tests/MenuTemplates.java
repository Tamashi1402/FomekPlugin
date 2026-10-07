import com.google.gson.*;
import freemarker.template.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Run with FreeMarker and Gson; compile the resulting Java against the injected runtime. */
public class MenuTemplates {
    public static class Workspace {
        public Workspace getWorkspace(){return this;}
        public Workspace getWorkspaceSettings(){return this;}
        public String getModElementsPackage(){return "net.tamashi.fomekcore";}
    }
    public static class Opt {
        public String toFloat(String value){return "((float)("+value+"))";}
        public String toInt(String value){return "((int)("+value+"))";}
    }
    public static void main(String[] args) throws Exception {
        Path resources=Path.of(args[0]),destination=Path.of(args[1]);
        Configuration cfg=new Configuration(Configuration.VERSION_2_3_33);
        cfg.setDirectoryForTemplateLoading(resources.resolve("neoforge-1.21.1/procedures").toFile());
        cfg.setDefaultEncoding("UTF-8");cfg.setLogTemplateExceptions(false);
        StringBuilder java=new StringBuilder("public class GeneratedMenuTemplates {\n");int count=0;
        try(var stream=Files.list(resources.resolve("procedures"))){for(Path file:stream.filter(f->f.getFileName().toString().startsWith("fomekmenu_")).sorted().toList()){
            JsonObject def=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String type=file.getFileName().toString().replace(".json","");
            if(!def.toString().contains("fomekmenu_studio_icon")&&!List.of("fomekmenu_studio_style","fomekmenu_get_control_text","fomekmenu_is_checked").contains(type))continue;
            Template template=cfg.getTemplate(type+".java.ftl");
            for(boolean rich:List.of(false,true)){
                Map<String,Object> data=new HashMap<>();data.put("w",new Workspace());data.put("opt",new Opt());data.put("package","net.tamashi.fomekcore");
                Matcher lists=Pattern.compile("input_list\\$[a-zA-Z0-9_]+").matcher(Files.readString(resources.resolve("neoforge-1.21.1/procedures/"+type+".java.ftl")));
                while(lists.find())data.put(lists.group(),List.of());
                for(var entry:def.entrySet())if(entry.getKey().matches("args\\d+"))for(JsonElement arg:entry.getValue().getAsJsonArray()){
                    JsonObject a=arg.getAsJsonObject();String kind=a.get("type").getAsString(),name=a.get("name").getAsString();
                    if(kind.equals("input_statement"))data.put("statement$"+name,rich?"localCounter++;":"");
                    else if(kind.equals("input_value")){
                        String check=a.has("check")&&a.get("check").isJsonPrimitive()?a.get("check").getAsString():"";
                        String value=switch(check){
                            case "Number" -> "20";case "Boolean" -> "false";case "String" -> "\"example\"";case "MCItem" -> "Items.STICK";
                            case "FomekMenuBox" -> "new net.tamashi.fomekcore.api.guisystems.Box(0, 0, 100, 40)";
                            case "FomekMenuObject" -> "(new net.tamashi.fomekcore.api.guisystems.MenuObject())";
                            case "FomekMenuStyle" -> rich?"net.tamashi.fomekcore.api.guisystems.MenuStyle.DEFAULT":"";
                            default -> "null";
                        };data.put("input$"+name,value);
                    }else if(kind.startsWith("field_")){
                        String value=kind.equals("field_dropdown")?a.getAsJsonArray("options").get(0).getAsJsonArray().get(1).getAsString():kind.equals("field_checkbox")?"false":a.has("text")?a.get("text").getAsString():"0";
                        data.put("field$"+name,value);
                    }
                }
                data.put("field$STUDIO_KEY",type);
                StringWriter result=new StringWriter();template.process(data,result);
                java.append("static void test").append(count++).append("(){int localCounter=0;\n");
                if(def.has("output"))java.append("Object value = ");
                java.append(result);if(def.has("output"))java.append(';');java.append("\n}\n");
            }
        }}
        java.append("}\n");Files.createDirectories(destination.getParent());Files.writeString(destination,java);
        System.out.println("PASS: "+count+" templates render with and without styles/actions and local-variable statements");
    }
}
