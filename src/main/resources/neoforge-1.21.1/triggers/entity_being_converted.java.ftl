<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onEntityConversion(LivingConversionEvent.Pre event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getEntity().getX()",
            "y": "event.getEntity().getY()",
            "z": "event.getEntity().getZ()",
            "entity": "event.getEntity()",
            "world": "event.getEntity().level()",
            "entityOutcome": "event.getOutcome().create(event.getEntity().level())",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }