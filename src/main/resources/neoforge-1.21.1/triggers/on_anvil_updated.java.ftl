<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getPlayer().getX()",
            "y": "event.getPlayer().getY()",
            "z": "event.getPlayer().getZ()",
            "entity": "event.getPlayer()",
            "world": "event.getPlayer().level()",
            "leftItem": "event.getLeft()",
            "rightItem": "event.getRight()",
            "output": "event.getOutput()",
            "itemName": "event.getName()",
            "levelCost": "event.getCost()",
            "materialCost": "event.getMaterialCost()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }