package net.tamashi.fomekcore.api.guisystems;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

/** Native clipboard/selection editing with font-aware wrapping and menu text rendering. */
public final class MenuTextInput extends AbstractWidget {
    private final MenuControls.Control owner;
    private static final class Model extends MultilineTextField {
        Model(){super(Minecraft.getInstance().font,Integer.MAX_VALUE/4);}
        int selectionStart(){return getSelected().beginIndex();}
        int selectionEnd(){return getSelected().endIndex();}
    }
    private final Model model;
    private final boolean multiline;
    private record Line(int start,int end) {}
    private final List<Line> lines=new ArrayList<>();
    private float scrollX,scrollY;
    private boolean adjusting;
    MenuTextInput(MenuControls.Control owner,int width,int height) {
        super(0,0,width,height,Component.literal(owner.label));
        this.owner=owner;multiline=owner.kind.equals("text_area");
        model=new Model();
        model.setCharacterLimit(owner.maxLength);
        model.setValue(clean(owner.text));
        owner.text=model.value();
        model.setValueListener(value->{
            if(adjusting)return;
            String clean=clean(value);
            if(!clean.equals(value)){adjusting=true;int cursor=model.cursor();model.setValue(clean);model.seekCursor(Whence.ABSOLUTE,Math.min(cursor,clean.length()));adjusting=false;}
            owner.text=model.value();
        });
    }
    private String clean(String text){return multiline?text:text.replace('\n',' ').replace('\r',' ');}
    private MenuStyle style(){MenuStyle s=StudioRuntime.style(owner.key);return (s==null?MenuStyle.DEFAULT:s).part("text");}
    private float scale(){return Math.max(.05f,style().size()/9f);}
    private float lineHeight(){return Math.max(1,style().size()+2);}
    private float measure(String text){return Minecraft.getInstance().font.width(style().literal(text))*scale();}
    private void layout(){
        lines.clear();String text=model.value();int start=0;
        for(int i=0;i<text.length();){
            int end=i+Character.charCount(text.codePointAt(i));
            if(multiline&&text.charAt(i)=='\n'){lines.add(new Line(start,i));start=end;}
            else if(multiline&&i>start&&measure(text.substring(start,end))>width-2){lines.add(new Line(start,i));start=i;}
            i=end;
        }
        lines.add(new Line(start,text.length()));
    }
    private int cursorLine(){int cursor=model.cursor();for(int i=0;i<lines.size();i++)if(cursor<=lines.get(i).end)return i;return lines.size()-1;}
    private int indexAt(Line line,double x){String value=model.value();float prev=0;for(int i=line.start;i<line.end;){int end=i+Character.charCount(value.codePointAt(i));float next=measure(value.substring(line.start,end));if(x<(prev+next)/2)return i;prev=next;i=end;}return line.end;}
    private void reveal(){layout();Line line=lines.get(cursorLine());float x=measure(model.value().substring(line.start,model.cursor()));float y=cursorLine()*lineHeight();
        if(multiline){scrollX=0;if(y<scrollY)scrollY=y;if(y+lineHeight()>scrollY+height)scrollY=y+lineHeight()-height;}
        else{scrollY=0;if(x<scrollX)scrollX=x;if(x+2>scrollX+width)scrollX=x+2-width;}
        scrollX=Math.max(0,scrollX);scrollY=Math.max(0,Math.min(scrollY,Math.max(0,lines.size()*lineHeight()-height)));
    }
    @Override protected void renderWidget(GuiGraphicsExtractor gui,int mx,int my,float partial){
        layout();MenuStyle s=style();MenuStyle outer=StudioRuntime.style(owner.key);if(model.value().isEmpty() && outer!=null)s=outer.part("placeholder");if(!s.hasText())return;
        gui.enableScissor(getX(),getY(),getX()+width,getY()+height);
        try{
            String value=model.value();
            for(int i=0;i<lines.size();i++){Line line=lines.get(i);float y=getY()+i*lineHeight()-scrollY;if(y+lineHeight()<getY()||y>getY()+height)continue;
                float x=getX()-scrollX;
                int a=Math.max(line.start,model.selectionStart()),b=Math.min(line.end,model.selectionEnd());
                if(a<b)gui.fill((int)(x+measure(value.substring(line.start,a))),(int)y,(int)(x+measure(value.substring(line.start,b))),(int)(y+lineHeight()),(outer==null||!outer.parts().containsKey("selection")?0x885588ff:outer.part("selection").background()));
                String text=value.isEmpty()?owner.label:value.substring(line.start,line.end);
                MenuText.draw(gui,s.literal(text),x,y,Math.max(.05f,s.size()/9f),s.textColor(),s.shadow(),s.smooth());
                if(isFocused()&&i==cursorLine()&&(System.currentTimeMillis()/500)%2==0){int cx=(int)(x+measure(value.substring(line.start,model.cursor())));gui.fill(cx,(int)y,cx+1,(int)(y+lineHeight()-1),s.textColor());}
            }
        }finally{gui.disableScissor();}
    }
    @Override public void onClick(double x,double y){layout();model.setSelecting(Screen.hasShiftDown());seek(x,y);}
    private void seek(double x,double y){int row=Math.max(0,Math.min(lines.size()-1,(int)((y-getY()+scrollY)/lineHeight())));model.seekCursor(Whence.ABSOLUTE,indexAt(lines.get(row),x-getX()+scrollX));reveal();}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(!isFocused()||button!=0)return false;layout();model.setSelecting(true);seek(x,y);model.setSelecting(false);return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!isFocused())return false;layout();model.setSelecting(Screen.hasShiftDown());
        int row=cursorLine();Line line=lines.get(row);
        if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){float x=measure(model.value().substring(line.start,model.cursor()));int next=Math.max(0,Math.min(lines.size()-1,row+(key==GLFW.GLFW_KEY_UP?-1:1)));model.seekCursor(Whence.ABSOLUTE,indexAt(lines.get(next),x));}
        else if((key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END)&&!Screen.hasControlDown())model.seekCursor(Whence.ABSOLUTE,key==GLFW.GLFW_KEY_HOME?line.start:line.end);
        else if(!multiline&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER))return true;
        else if(!model.keyPressed(key))return false;
        reveal();return true;
    }
    @Override public boolean charTyped(char c,int mods){if(!isFocused()||!StringUtil.isAllowedChatCharacter(c))return false;model.insertText(String.valueOf(c));reveal();return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!multiline||!isMouseOver(x,y))return false;layout();scrollY=(float)Math.max(0,Math.min(Math.max(0,lines.size()*lineHeight()-height),scrollY-dy*lineHeight()*3));return true;}
    @Override protected void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,Component.literal(owner.label+": "+model.value()));}
}

