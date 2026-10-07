package net.tamashi.fomekcore.api.guisystems;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Supersampled, linearly filtered GUI text. Keeps font size exact at fractional scales. */
public final class MenuText {
    private static final int SAMPLE=4, LIMIT=96;
    private record Key(String text,String font,int color,boolean shadow) {}
    private record Layer(TextureTarget target,int width,int height) {}
    private static final LinkedHashMap<Key,Layer> cache=new LinkedHashMap<>(16,.75f,true);
    private MenuText() {}
    public static void clear(){for(Layer l:cache.values())l.target.destroyBuffers();cache.clear();}
    public static void draw(GuiGraphics gui,Component text,float x,float y,float scale,int color,boolean shadow,boolean smooth){
        if(text.getString().isEmpty()||scale<=0)return;
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        gui.flush();RenderSystem.setShaderFogStart(1e20f);RenderSystem.setShaderFogEnd(2e20f);
        try{
            if(!smooth || Minecraft.getInstance().font.width(text)>4096){gui.pose().pushPose();try{gui.pose().translate(x,y,0);gui.pose().scale(scale,scale,1);gui.drawString(Minecraft.getInstance().font,text,0,0,color,shadow);gui.flush();}finally{gui.pose().popPose();}return;}
            Key key=new Key(text.getString(),text.getStyle().getFont().toString(),color,shadow);
            Layer layer=cache.get(key);
            if(layer==null){layer=rasterize(text,color,shadow);cache.put(key,layer);if(cache.size()>LIMIT){var iterator=cache.entrySet().iterator();var oldest=iterator.next();oldest.getValue().target.destroyBuffers();iterator.remove();}}
            boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND);float[] shaderColor=RenderSystem.getShaderColor().clone();
            RenderSystem.disableDepthTest();RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,layer.target.getColorTextureId());
            Matrix4f matrix=gui.pose().last().pose();float left=x-scale,top=y-scale,right=left+layer.width*scale,bottom=top+layer.height*scale;
            var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(matrix,left,bottom,0).setUv(0,0);buffer.addVertex(matrix,right,bottom,0).setUv(1,0);
            buffer.addVertex(matrix,right,top,0).setUv(1,1);buffer.addVertex(matrix,left,top,0).setUv(0,1);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);if(depth)RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();if(!blend)RenderSystem.disableBlend();
        }finally{RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);}
    }
    private static Layer rasterize(Component text,int color,boolean shadow){
        Minecraft mc=Minecraft.getInstance();int width=Math.max(1,mc.font.width(text)+3),height=mc.font.lineHeight+3;
        // Bound allocations for arbitrarily long user strings; rendering still works.
        int samples=Math.max(1,Math.min(SAMPLE,8192/Math.max(width,height)));
        int drawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
        TextureTarget target=null;
        try{
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            target=new TextureTarget(width*samples,height*samples,true,Minecraft.ON_OSX);target.setFilterMode(GL11.GL_LINEAR);target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            modelView.identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,width*samples,height*samples,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            GuiGraphics surface=new GuiGraphics(mc,mc.renderBuffers().bufferSource());surface.pose().translate(0,0,-11000);surface.pose().scale(samples,samples,1);
            surface.drawString(mc.font,text,1,1,color,shadow);surface.flush();
            return new Layer(target,width,height);
        }catch(RuntimeException e){if(target!=null)target.destroyBuffers();throw e;}
        finally{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawFbo);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);RenderSystem.setProjectionMatrix(projection,sorting);
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }
}
