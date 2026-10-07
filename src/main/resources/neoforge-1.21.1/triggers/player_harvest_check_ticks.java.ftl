<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getPos().getX()",
            "y": "event.getPos().getY()",
            "z": "event.getPos().getZ()",
            "entity": "event.getEntity()",
            "world": "event.getEntity().level()",
            "blockstate": "event.getTargetBlock()",
            "canHarvest": "event.canHarvest()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }