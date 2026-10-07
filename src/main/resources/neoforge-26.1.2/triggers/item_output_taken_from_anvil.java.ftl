<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onItemTakenFromAnvil(AnvilRepairEvent event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getEntity().getX()",
            "y": "event.getEntity().getY()",
            "z": "event.getEntity().getZ()",
            "entity": "event.getEntity()",
            "world": "event.getEntity().level()",
            "leftItem": "event.getLeft()",
            "rightItem": "event.getRight()",
            "output": "event.getOutput()",
            "breakChance": "event.getBreakChance()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }