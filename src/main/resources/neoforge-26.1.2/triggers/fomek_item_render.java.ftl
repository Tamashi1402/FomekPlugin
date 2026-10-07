<#include "procedures.java.ftl">
@net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class ${name}Procedure {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onFomekItemRender(${package}.api.render.RenderEvent.Item event) {
        <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
            "x": "event.getX()",
            "y": "event.getY()",
            "z": "event.getZ()",
            "world": "event.getWorld()",
            "entity": "event.getEntity()",
            "itemstack": "event.getItemStack()",
            "partialTick": "event.getPartialTick()"
        }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,${dependenciesCode}</#if>);
    }
