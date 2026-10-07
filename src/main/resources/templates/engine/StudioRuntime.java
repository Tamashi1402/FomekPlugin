package net.tamashi.fomekcore.api.guisystems;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Per-menu event and style state. Actions execute inline in the generated procedure. */
public final class StudioRuntime {
    private static final Deque<String> scopes=new ArrayDeque<>();
    private static final Map<String,Node> nodes=new LinkedHashMap<>();
    private static long frame;
    private static String held;
    private static class Node {
        MenuStyle normal,hover,pressed,clicked;
        float x,y,w,h;
        String element="";
        long seen=-2,updated=-1,clickedFrame=-2;
        boolean click,check;
    }
    private StudioRuntime() {}
    /** Runtime style overrides set from actions: key -> (property -> value). */
    private static final Map<String,Map<String,Object>> overrides=new LinkedHashMap<>();
    /** Resolve an element id (e.g. "btn1") to its studio key ("studio_x:btn1").
     *  Nested keys ("parent/child") match their trailing id as well. */
    public static String keyFor(String id){
        if(id==null)return null;id=id.trim();if(id.isEmpty())return key();
        String found=null;
        for(String k:nodes.keySet()){
            if(k.equals(id)||k.endsWith(":"+id)||k.endsWith("/"+id)){found=k;}
        }
        return found;
    }
    public static MenuStyle applyOverrides(String key,MenuStyle style){
        Map<String,Object> o=overrides.get(key);
        if(o==null||style==null)return style;
        return new MenuStyle(
            o.containsKey("background")?((Number)o.get("background")).intValue():style.background(),
            o.containsKey("border")?((Number)o.get("border")).intValue():style.border(),
            o.containsKey("textColor")?((Number)o.get("textColor")).intValue():style.textColor(),
            o.containsKey("text")?String.valueOf(o.get("text")):style.text(),
            style.font(),
            o.containsKey("size")?((Number)o.get("size")).floatValue():style.size(),
            style.texture(),
            o.containsKey("hasText")?(Boolean)o.get("hasText"):style.hasText(),
            style.buttonStyle(),style.smooth(),style.borderWidth(),
            o.containsKey("paddingX")?((Number)o.get("paddingX")).intValue():style.paddingX(),
            o.containsKey("paddingY")?((Number)o.get("paddingY")).intValue():style.paddingY(),
            style.align(),style.shadow(),style.parts());
    }
    private static void override(String id,String property,Object value){
        String k=keyFor(id);if(k==null)return;
        overrides.computeIfAbsent(k,x->new LinkedHashMap<>()).put(property,value);
    }
    /** Effective style of an element id (state + overrides), or null. */
    public static MenuStyle getElementStyle(String id){String k=keyFor(id);return k==null?null:style(k);}
    public static int getStyleBackground(String id){MenuStyle s=getElementStyle(id);return s==null?MenuStyle.DEFAULT.background():s.background();}
    public static int getStyleBorder(String id){MenuStyle s=getElementStyle(id);return s==null?MenuStyle.DEFAULT.border():s.border();}
    public static int getStyleTextColor(String id){MenuStyle s=getElementStyle(id);return s==null?MenuStyle.DEFAULT.textColor():s.textColor();}
    public static float getStyleSize(String id){MenuStyle s=getElementStyle(id);return s==null?MenuStyle.DEFAULT.size():s.size();}
    public static String getStyleText(String id){MenuStyle s=getElementStyle(id);return s==null?"":s.text();}
    public static void setStyleBackground(String id,int v){override(id,"background",v);}
    public static void setStyleBorder(String id,int v){override(id,"border",v);}
    public static void setStyleTextColor(String id,int v){override(id,"textColor",v);}
    public static void setStyleSize(String id,float v){override(id,"size",v);}
    public static void setStyleText(String id,String v){override(id,"text",v==null?"":v);}
    public static void setStyleHasText(String id,boolean v){override(id,"hasText",v);}
    /** Element state query for the Data blocks: hovered / held / clicked / checked. */
    public static boolean isState(String id,String state){
        String k=keyFor(id);if(k==null)return false;Node n=nodes.get(k);if(n==null)return false;
        return switch(state==null?"":state){
            case "hovered","hover" -> hovered(k);
            case "held" -> k.equals(held);
            case "clicked","click" -> n.click;
            case "checked","check" -> n.check;
            default -> false;
        };
    }
    public static void beginFrame() {frame++;scopes.clear();nodes.entrySet().removeIf(e->{if(e.getValue().seen<frame-600){overrides.remove(e.getKey());return true;}return false;});}
    public static void clear() {scopes.clear();nodes.clear();held=null;overrides.clear();MenuControls.clear();MenuText.clear();}
    public static void push(String id,MenuStyle normal,MenuStyle hover,MenuStyle pressed,MenuStyle clicked) {
        String key=(scopes.isEmpty()?String.valueOf(VirtualGui.getCurrentId()):scopes.peek())+"/"+id;
        scopes.push(key);Node n=nodes.computeIfAbsent(key,k->new Node());n.normal=normal;n.hover=hover;n.pressed=pressed;n.clicked=clicked;
    }
    public static void pop(){if(!scopes.isEmpty())scopes.pop();}
    public static String key(){return scopes.isEmpty()?null:scopes.peek();}
    public static void hit(String key,float x,float y,float w,float h,String element){
        if(key==null)return;Node n=nodes.computeIfAbsent(key,k->new Node());n.x=x;n.y=y;n.w=Math.max(0,w);n.h=Math.max(0,h);n.element=element;n.seen=frame;
        nodes.remove(key);nodes.put(key,n);
    }
    private static boolean contains(Node n,double x,double y){return n.seen>=frame-1&&x>=n.x&&x<n.x+n.w&&y>=n.y&&y<n.y+n.h;}
    private static String top(double x,double y){String result=null;for(var e:nodes.entrySet())if(contains(e.getValue(),x,y))result=e.getKey();return result;}
    private static boolean hovered(String key){String top=top(GuiState.getMouseX(null),GuiState.getMouseY(null));return top!=null&&key!=null&&(top.equals(key)||top.startsWith(key+"/"));}
    public static String press(double x,double y){held=top(x,y);if(held!=null){nodes.get(held).click=true;nodes.get(held).clickedFrame=frame+1;}return held;}
    public static void release(){held=null;}
    public static void checked(String key){Node n=nodes.get(key);if(n!=null)n.check=true;}
    public static MenuStyle style(String key){
        Node n=nodes.get(key);if(n==null)return null;
        if(n.clickedFrame>=frame&&n.clicked!=null)return applyOverrides(key,n.clicked);
        if(Objects.equals(key,held)&&n.pressed!=null)return applyOverrides(key,n.pressed);
        if(hovered(key)&&n.hover!=null)return applyOverrides(key,n.hover);
        return applyOverrides(key,n.normal);
    }
    public static MenuStyle style(){return style(key());}
    public static float textWidth(String text){MenuStyle style=style();if(style!=null)style=style.part("text");return style==null?net.minecraft.client.Minecraft.getInstance().font.width(text):net.minecraft.client.Minecraft.getInstance().font.width(style.component(text))*Math.max(.05f,style.size()/9f);}
    public static float textHeight(){MenuStyle style=style();if(style!=null)style=style.part("text");return style==null?9:Math.max(.45f,style.size());}
    public static boolean event(String event){
        Node n=nodes.get(key());if(n==null)return false;boolean fire=switch(event){
            case "hover" -> hovered(key());
            case "click" -> n.click;
            case "check" -> n.check;
            case "update" -> n.updated!=frame;
            default -> false;
        };
        if(fire){if(event.equals("click"))n.click=false;if(event.equals("check"))n.check=false;if(event.equals("update"))n.updated=frame;
            VirtualGui.setActionReason(event);VirtualGui.setActionElementId(n.element);VirtualGui.setActionMouseX(GuiState.getMouseX(null));VirtualGui.setActionMouseY(GuiState.getMouseY(null));}
        return fire;
    }
}
