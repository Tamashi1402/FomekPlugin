<#include "procedures.java.ftl">
public class ${name}Procedure {
    public static void placeholderEvent(PlaceholderEvent event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.pos.x()",
            "y": "event.pos.y()",
            "z": "event.pos.z()",
            "entity": "event.entity",
            "world": "event.level",
            "sourceentity": "event.sourceEntity",
            "immediatesourceentity": "event.immediateSourceEntity",
            "blockstate": "event.blockState",
			"itemstack": "event.itemStack",
			"damagesource": "event.damageSource",
			"direction": "event.direction",
			"dimension": "event.dimension"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }