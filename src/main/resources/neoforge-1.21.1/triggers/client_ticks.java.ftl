<#include "procedures.java.ftl">
@EventBusSubscriber(value = Dist.CLIENT)
public class ${name}Procedure {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        <#assign needsWorld = false>
        <#assign needsPlr = false>
        <#list dependencies as dep>
            <#assign depName = dep.getName()>
            <#if depName == "world">
                <#assign needsWorld = true>
            </#if>
            <#if depName == "x" || depName == "y" || depName == "z">
                <#assign needsPlr = true>
            </#if>
        </#list>
        <#if needsWorld>
        if (mc.level == null)
        return;
        </#if>
        <#if needsPlr>
        if (mc.player == null)
        return;
        </#if>
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "mc.player.getX()",
            "y": "mc.player.getY()",
            "z": "mc.player.getZ()",
            "entity": "mc.player",
            "world": "mc.level",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }