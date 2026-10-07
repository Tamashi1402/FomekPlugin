package net.tamashi.fomekcore.api.guisystems;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Stateful native text inputs, checkboxes and dropdowns, scoped to the open menu. */
public final class MenuControls {
    static final Map<String,Control> controls=new LinkedHashMap<>();
    static Control focus;
    public static final class Control {
        final String id,kind;
        String text,label,key;
        boolean checked,open;
        int maxLength,rows,first,x,y,w,h;
        long seen;
        List<String> items=List.of();
        AbstractWidget widget;
        Control(String id,String kind,String initial,boolean checked){this.id=id;this.kind=kind;this.text=initial==null?"":initial;this.checked=checked;}
        void configure(String label,int limit,Object values,int rows){
            this.label=label==null?"":label;this.maxLength=Math.max(1,limit);this.rows=Math.max(1,Math.min(30,rows));
            List<String> next=new ArrayList<>();
            if(values instanceof Iterable<?> list)for(Object item:list)next.add(String.valueOf(item));
            else if(values instanceof Object[] array)for(Object item:array)next.add(String.valueOf(item));
            else if(values!=null&&!values.toString().isEmpty())next.addAll(Arrays.asList(values.toString().split("\\R",-1)));
            items=List.copyOf(next);if(kind.equals("dropdown")&&!items.contains(text))text=items.isEmpty()?"":items.get(0);
        }
        void ensureWidget(int width,int height){
            if(!kind.equals("inputfield")&&!kind.equals("text_area"))return;
            if(widget!=null&&widget.getWidth()==width&&widget.getHeight()==height)return;
            widget=new MenuTextInput(this,width,height);
            widget.setFocused(focus==this);
        }
        public void render(GuiGraphics gui,int x,int y,int w,int h,int mx,int my,float partial,String key){
            this.x=x;this.y=y;this.w=w;this.h=h;this.key=key;seen=System.currentTimeMillis();
            MenuStyle style=StudioRuntime.style(key);if(style==null)style=MenuStyle.DEFAULT;
            style.background(gui,x,y,w,h);
            ensureWidget(Math.max(1,w-2*style.paddingX()),Math.max(1,h-2*style.paddingY()));
            if(widget!=null){widget.setX(x+style.paddingX());widget.setY(y+style.paddingY());widget.render(gui,mx,my,partial);}
            else if(kind.equals("checkbox")){int side=Math.min(h,w);gui.fill(x+3,y+3,x+side-3,y+side-3,style.border());if(checked)style.part("indicator").valueText(gui,"✓",x+style.part("indicator").paddingX(),y+style.part("indicator").paddingY());if(label!=null&&!label.isEmpty())style.part("label").text(gui,label,x+side+style.part("label").paddingX(),y+style.part("label").paddingY(),false);}
            else {style.part("text").valueText(gui,text,x+style.part("text").paddingX(),y+style.part("text").paddingY());style.part("indicator").valueText(gui,"▾",x+w-12,y+style.part("indicator").paddingY());}
        }
        boolean click(double mx,double my,int button){
            if(button!=0)return false;
            if(kind.equals("checkbox")){checked=!checked;StudioRuntime.checked(key);return true;}
            if(kind.equals("dropdown")){open=!open;first=Math.max(0,Math.min(items.indexOf(text),Math.max(0,items.size()-rows)));return true;}
            return widget!=null&&widget.mouseClicked(mx,my,button);
        }
    }
    public static Control configure(String id,String kind,String initial,String label,int max,boolean checked,Object items,int rows){
        Control c=controls.get(id);if(c==null||!c.kind.equals(kind)){c=new Control(id,kind,initial,checked);controls.put(id,c);}c.configure(label,max,items,rows);return c;
    }
    public static String text(String id){Control c=controls.get(id);return c==null?"":c.text;}
    /** Writes the control's text/selection (input field, text area, dropdown). */
    public static void setText(String id,String value){Control c=controls.get(id);if(c!=null)c.text=value==null?"":value;}
    public static boolean checked(String id){Control c=controls.get(id);return c!=null&&c.checked;}
    private static void focus(Control next){if(focus!=null&&focus!=next){focus.open=false;if(focus.widget!=null)focus.widget.setFocused(false);}focus=next;if(next!=null&&next.widget!=null)next.widget.setFocused(true);}
    public static boolean click(double x,double y,int button){
        if(button==0&&focus!=null&&focus.open){Control c=focus;int top=listY(c),height=Math.min(c.rows,c.items.size())*rowHeight(c);
            if(x>=c.x&&x<c.x+c.w&&y>=top&&y<top+height){int index=c.first+(int)(y-top)/rowHeight(c);if(index<c.items.size())c.text=c.items.get(index);c.open=false;return true;}}
        String key=StudioRuntime.press(x,y);Control target=null;
        for(Control c:controls.values())if(Objects.equals(key,c.key)&&System.currentTimeMillis()-c.seen<1000)target=c;
        focus(target);return target!=null&&target.click(x,y,button);
    }
    public static boolean key(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_TAB){List<Control> active=new ArrayList<>();for(Control value:controls.values())if(System.currentTimeMillis()-value.seen<500)active.add(value);if(active.isEmpty())return false;int i=active.indexOf(focus);focus(active.get(Math.floorMod(i+((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),active.size())));return true;}
        if(focus==null)return false;Control c=focus;
        if(key==GLFW.GLFW_KEY_ESCAPE){focus(null);return true;}
        if(c.widget!=null){c.widget.keyPressed(key,scan,mods);return true;}
        if(c.kind.equals("checkbox")&&(key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_ENTER)){c.checked=!c.checked;StudioRuntime.checked(c.key);return true;}
        if(c.kind.equals("dropdown")){int index=Math.max(0,c.items.indexOf(c.text));if(key==GLFW.GLFW_KEY_DOWN)index++;else if(key==GLFW.GLFW_KEY_UP)index--;else if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE)c.open=!c.open;
            if(!c.items.isEmpty())c.text=c.items.get(Math.max(0,Math.min(c.items.size()-1,index)));return true;}
        return false;
    }
    public static boolean typed(char c,int modifiers){return focus!=null&&focus.widget!=null&&focus.widget.charTyped(c,modifiers);}
    public static boolean drag(double x,double y,int button,double dx,double dy){return focus!=null&&focus.widget!=null&&focus.widget.mouseDragged(x,y,button,dx,dy);}
    public static boolean scroll(double x,double y,double dx,double dy){if(focus==null)return false;if(focus.open){focus.first=Math.max(0,Math.min(Math.max(0,focus.items.size()-focus.rows),focus.first-(int)Math.signum(dy)));return true;}return focus.widget!=null&&focus.widget.mouseScrolled(x,y,dx,dy);}
    private static int rowHeight(Control c){MenuStyle style=StudioRuntime.style(c.key);return Math.max(14,(int)Math.ceil(style==null?9:style.part("list").size())+5);}
    private static int listY(Control c){int height=Math.min(c.rows,c.items.size())*rowHeight(c);return c.y+c.h+height>Minecraft.getInstance().getWindow().getGuiScaledHeight()?Math.max(0,c.y-height):c.y+c.h;}
    public static void renderDropdown(GuiGraphics gui){if(focus==null||!focus.open)return;Control c=focus;if(System.currentTimeMillis()-c.seen>500){focus(null);return;}MenuStyle style=StudioRuntime.style(c.key);if(style==null)style=MenuStyle.DEFAULT;style=style.part("list");int y=listY(c);gui.pose().pushPose();gui.pose().translate(0,0,400);
        for(int row=0;row<c.rows&&row+c.first<c.items.size();row++){style.background(gui,c.x,y+row*rowHeight(c),c.w,rowHeight(c));style.valueText(gui,c.items.get(row+c.first),c.x+style.paddingX(),y+row*rowHeight(c)+style.paddingY());}gui.pose().popPose();gui.flush();}
    public static void clear(){controls.clear();focus=null;}
}


