<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onMobEffectEvent(MobEffectEvent.Expired event) {
		if (event != null && event.getEntity() != null && event.getEffectInstance() != null) {
        
		    String effect = event.getEffectInstance().toString();
            
    		int level = new Object() {
                int convert(String s) {
                    try {
                        return (int) Double.parseDouble(s.trim());
                    } catch (Exception e) {
                    }
                    return 0;
                }
            }.convert(effect.substring(effect.indexOf("x ") + "x ".length(), effect.indexOf(",")));
            level = Math.max(1, level);
        
            effect = effect.replace("effect.", "").replace(".", ":").replace(",", "");
            effect = effect.substring(0, effect.indexOf(" "));
        
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"effect": "effect",
				"level": "level",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}