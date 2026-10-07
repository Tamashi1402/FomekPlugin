<#include "procedures.java.ftl">
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ${name}Procedure {
    @SubscribeEvent
    public static void onComponentModification(ModifyDefaultComponentsEvent event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }