<#include "procedures.java.ftl">
@net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class ${name}Procedure {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onFomekOverlayRender(${package}.api.render.RenderEvent.Overlay event) {
        <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
            "entity": "event.getPlayer()",
            "world": "event.getPlayer() != null ? event.getPlayer().level() : null",
            "partialTick": "event.getPartialTick()"
        }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,${dependenciesCode}</#if>);
    }
