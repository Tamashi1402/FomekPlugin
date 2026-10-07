<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onEntityConversion(LivingConversionEvent.Post event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getEntity().getX()",
            "y": "event.getEntity().getY()",
            "z": "event.getEntity().getZ()",
            "entity": "event.getOutcome()",
            "world": "event.getEntity().level()",
            "entityPre": "event.getEntity()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }