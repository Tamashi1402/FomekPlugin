package net.tamashi.fomek.menus;

import net.mcreator.plugin.events.ui.BlocklyPanelRegisterDOMData;
import net.mcreator.ui.MCreator;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.io.*;

/** Read-only resource bridge for the procedure editor's local preview. */
public final class StudioAssets {
    // WebKit keeps Java bridges weakly: retain each bridge for its panel lifetime.
    private static final Map<Object,StudioAssets> BRIDGES=Collections.synchronizedMap(new WeakHashMap<>());
    private final Path resourceRoot;
    private final Map<String,String> cache=new HashMap<>();
    private List<Path> minecraftJars;
    private StudioAssets(MCreator mcreator){this.resourceRoot=mcreator.getWorkspace()==null?null:mcreator.getWorkspace().getWorkspaceFolder().toPath().resolve("src/main/resources").toAbsolutePath().normalize();}
    StudioAssets(Path resources){this.resourceRoot=resources.toAbsolutePath().normalize();}
    public static void register(BlocklyPanelRegisterDOMData event){
        var bridge=new StudioAssets(event.getBlocklyPanel().getMCreator());
        BRIDGES.put(event.getBlocklyPanel(),bridge);
        event.addJavaScriptBridge("fomekstudioassets",bridge);
    }
    /** Only resource-pack paths are accepted, never arbitrary filesystem paths. */
    @SuppressWarnings("unused") public synchronized String asset(String resource){
        if(resource==null||!resource.matches("[a-z0-9_.-]+:[a-zA-Z0-9_./-]+")||resource.contains(".."))return "";
        String lower=resource.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".png")&&!lower.endsWith(".json")&&!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))return "";
        if(cache.containsKey(resource))return cache.get(resource);
        String entry="assets/"+resource.replace(':','/');byte[] data=null;
        try{
            if(resourceRoot!=null){Path root=resourceRoot;Path path=root.resolve(entry).normalize();
                if(path.startsWith(root)&&Files.isRegularFile(path)&&Files.size(path)<=8*1024*1024)data=Files.readAllBytes(path);
            }
            if(data==null&&resource.startsWith("minecraft:"))for(Path jar:vanillaJars()){
                try(ZipFile zip=new ZipFile(jar.toFile())){var item=zip.getEntry(entry);if(item!=null&&item.getSize()>=0&&item.getSize()<=8*1024*1024){try(InputStream in=zip.getInputStream(item)){data=in.readNBytes(8*1024*1024+1);}break;}}
                catch(IOException ignored){}
            }
        }catch(IOException ignored){}
        String result="";
        if(data!=null&&data.length<=8*1024*1024){
            if(lower.endsWith(".json"))result=new String(data,java.nio.charset.StandardCharsets.UTF_8);
            else result="data:"+(lower.endsWith(".png")?"image/png":lower.endsWith(".ttf")?"font/ttf":"font/otf")+";base64,"+Base64.getEncoder().encodeToString(data);
        }
        cache.put(resource,result);return result;
    }
    private List<Path> vanillaJars(){
        if(minecraftJars!=null)return minecraftJars;
        var found=new ArrayList<Path>();String home=System.getProperty("user.home");
        for(Path root:List.of(Path.of(home,".gradle","caches"),Path.of(home,".mcreator","gradle","caches"))){
            if(!Files.isDirectory(root))continue;
            try(var paths=Files.find(root,14,(p,a)->a.isRegularFile()&&p.toString().endsWith(".jar")&&p.toString().contains("26.1")&&(p.getFileName().toString().contains("minecraft")||p.getFileName().toString().contains("client"))&&!p.toString().contains("sources")&&!p.toString().contains("javadoc"))){found.addAll(paths.limit(32).toList());}
            catch(IOException ignored){}
        }
        minecraftJars=List.copyOf(found);return minecraftJars;
    }
    @SuppressWarnings("unused") public synchronized void refresh(){cache.clear();minecraftJars=null;}
}
