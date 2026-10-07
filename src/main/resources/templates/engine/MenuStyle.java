package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

/** Complete visual state; missing hover/held states inherit the normal state. */
public record MenuStyle(int background, int border, int textColor, String text, String font,
                        float size, String texture, boolean hasText, String buttonStyle, boolean smooth,
                        int borderWidth, int paddingX, int paddingY, String align, boolean shadow,
                        java.util.Map<String,MenuStyle> parts) {
    public MenuStyle(int background,int border,int textColor,String text,String font,float size,String texture,boolean hasText,String buttonStyle,boolean smooth){
        this(background,border,textColor,text,font,size,texture,hasText,buttonStyle,smooth,1,4,3,"left",false,java.util.Map.of());
    }
    public MenuStyle withLayout(int borderWidth,int paddingX,int paddingY,String align,boolean shadow){
        return new MenuStyle(background,border,textColor,text,font,size,texture,hasText,buttonStyle,smooth,Math.max(0,borderWidth),Math.max(0,paddingX),Math.max(0,paddingY),align,shadow,parts);
    }
    public MenuStyle withPart(String name,MenuStyle value){var copy=new java.util.HashMap<>(parts);copy.put(name,value);return new MenuStyle(background,border,textColor,text,font,size,texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,java.util.Map.copyOf(copy));}
    public MenuStyle part(String name){return parts.getOrDefault(name,this);}
    public MenuStyle withBackground(int v){return new MenuStyle(v,border,textColor,text,font,size,texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public MenuStyle withBorder(int v){return new MenuStyle(background,v,textColor,text,font,size,texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public MenuStyle withTextColor(int v){return new MenuStyle(background,border,v,text,font,size,texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public MenuStyle withText(String v){return new MenuStyle(background,border,textColor,v==null?"":v,font,size,texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public MenuStyle withSize(float v){return new MenuStyle(background,border,textColor,text,font,Math.max(.45f,v),texture,hasText,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public MenuStyle withHasText(boolean v){return new MenuStyle(background,border,textColor,text,font,size,texture,v,buttonStyle,smooth,borderWidth,paddingX,paddingY,align,shadow,parts);}
    public static final MenuStyle DEFAULT = new MenuStyle(0xff252525, 0xff777777, -1, "", "minecraft:default", 9, "", true, "flat", true);
    public Component component(String fallback) {
        String content = text == null || text.isEmpty() ? fallback : text;
        return literal(content);
    }
    public Component literal(String content) {
        Identifier id = Identifier.tryParse(font == null ? "minecraft:default" : font);
        return Component.literal(content == null ? "" : content).withStyle(Style.EMPTY.withFont(id == null ? Identifier.withDefaultNamespace("default") : id));
    }
    public void background(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        if (!"none".equals(buttonStyle) && !"outline".equals(buttonStyle)) gui.fill(x,y,x+w,y+h,background);
        if (texture != null && !texture.isBlank()) {
            Identifier id=Identifier.tryParse(texture);
            if (id != null) gui.blit(id,x,y,0,0,w,h,w,h);
        }
        if (!"none".equals(buttonStyle)) {
            int line=Math.min(borderWidth,Math.max(0,Math.min(w,h)/2));
            gui.fill(x,y,x+w,y+line,border); gui.fill(x,y+h-line,x+w,y+h,border);
            gui.fill(x,y,x+line,y+h,border); gui.fill(x+w-line,y,x+w,y+h,border);
            if ("raised".equals(buttonStyle)) {gui.fill(x+1,y+1,x+w-1,y+2,0x55ffffff);gui.fill(x+1,y+h-2,x+w-1,y+h-1,0x55000000);}
        }
    }
    public void text(GuiGraphicsExtractor gui, String fallback, float x, float y, boolean shadow) {
        MenuStyle s=part("text");if (!s.hasText) return;
        MenuText.draw(gui,s.component(fallback),x,y,Math.max(.05f,s.size/9f),s.textColor,shadow||s.shadow,s.smooth);
    }
    public void valueText(GuiGraphicsExtractor gui, String value, float x, float y) {
        if (hasText) MenuText.draw(gui,literal(value),x,y,Math.max(.05f,size/9f),textColor,shadow,smooth);
    }
    public void label(GuiGraphicsExtractor gui,String fallback,int x,int y,int w,int h){
        MenuStyle s=part("text");float width=Minecraft.getInstance().font.width(s.component(fallback))*Math.max(.05f,s.size/9f);
        float px="center".equals(s.align)?x+(w-width)/2:"right".equals(s.align)?x+w-width-s.paddingX:x+s.paddingX;
        s.text(gui,fallback,px,y+s.paddingY,s.shadow);
    }
}
