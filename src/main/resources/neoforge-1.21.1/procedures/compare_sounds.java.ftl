<#assign sound = generator.map(field$sound, "sounds")?replace("CUSTOM:", "${modid}:")>
<#include "redwires_plugin_utils.ftl">
(event instanceof PlayLevelSoundEvent _sound${index} ? _sound${index}.getSound().is(ResourceLocation.parse("${sound}")) : false)