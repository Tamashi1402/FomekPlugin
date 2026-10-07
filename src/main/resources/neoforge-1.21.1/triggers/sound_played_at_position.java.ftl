<#include "procedures.java.ftl">
@EventBusSubscriber
public class ${name}Procedure {
    @SubscribeEvent
    public static void onSoundPlayed(PlayLevelSoundEvent.AtPosition event) {
        <#assign dependenciesCode><#compress>
            <@procedureDependenciesCode dependencies, {
            "x": "event.getPosition().x()",
            "y": "event.getPosition().y()",
            "z": "event.getPosition().z()",
            "world": "event.getLevel()",
            "pitch": "event.getNewPitch()",
            "volume": "event.getNewVolume()",
            "event": "event"
            }/>
        </#compress></#assign>
        execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }