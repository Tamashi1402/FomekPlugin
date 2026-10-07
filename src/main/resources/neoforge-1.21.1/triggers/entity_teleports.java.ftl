<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onEntityTeleportation(EntityTeleportEvent event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getPrevX()",
            "y": "event.getPrevY()",
            "z": "event.getPrevZ()",
            "entity": "event.getEntity()",
            "world": "event.getEntity().level()",
            "targetX": "event.getTargetX()",
            "targetY": "event.getTargetY()",
            "targetZ": "event.getTargetZ()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }