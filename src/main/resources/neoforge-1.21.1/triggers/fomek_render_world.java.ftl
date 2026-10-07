<#include "procedures.java.ftl">
@net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class ${name}Procedure {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onFomekWorldRender(${package}.api.render.RenderEvent.World event) {
        <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
            "x": "event.getX()",
            "y": "event.getY()",
            "z": "event.getZ()",
            "world": "event.getWorld()",
            "entity": "event.getEntity()",
            "renderTime": "event.getRenderTime()",
            "partialTick": "event.getPartialTick()"
        }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,${dependenciesCode}</#if>);
    }
