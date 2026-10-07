<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
<#include "procedures.java.ftl">
@net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class ${name}Procedure {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onFomekMenuRender(${fomek}.GuiState.RenderEvent event) {
        // Register the menu's GuiGraphics with the page flip system so
        // page flip blocks work on panels made of any menu components.
        ${fomek}.PageFlip.beginRender(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
            "entity": "event.getPlayer()",
            "world": "event.getPlayer() != null ? event.getPlayer().level() : null",
            "guigraphics": "event.getGuiGraphics()",
            "partialTick": "event.getPartialTick()",
            "mouseX": "event.getMouseX()",
            "mouseY": "event.getMouseY()",
            "screenWidth": "event.getScreenWidth()",
            "screenHeight": "event.getScreenHeight()"
        }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,${dependenciesCode}</#if>);
        ${fomek}.PageFlip.endRender();
    }
