<#include "procedures.java.ftl">
@Mod.EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onLeftClickItem(PlayerInteractEvent.LeftClickEmpty event) {
        <#-- Change to PlayerInteractEvent.LeftClickBlock if you want to detect left-click on blocks -->
        <#-- if (event.getHand() != event.getEntity().getUsedItemHand()) return; -->
        <#-- If the above line is not necessary for left-click event, it can be removed -->
        <#assign dependenciesCode>
            <#compress>
                <@procedureDependenciesCode dependencies, {
                    "x": "event.getEntity().getX()",
                    "y": "event.getEntity().getY()",
                    "z": "event.getEntity().getZ()",
                    "world": "event.getLevel()",
                    "entity": "event.getEntity()",
                    "event": "event"
                }/>
            </#compress>
        </#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }
