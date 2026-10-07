<#include "procedures.java.ftl">
@EventBusSubscriber(Dist.CLIENT) public class ${name}Procedure {
	@SubscribeEvent public static void afterModelRegistration(EntityRenderersEvent.AddLayers event) {
		<#assign dependenciesCode>
			<@procedureDependenciesCode dependencies, {
				"event": "event"
			}/>
		</#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}