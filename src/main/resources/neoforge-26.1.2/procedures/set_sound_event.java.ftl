<#assign sound = generator.map(field$sound, "sounds")?replace("CUSTOM:", "${modid}:")>
<#include "redwires_plugin_utils.ftl">
if (event instanceof PlayLevelSoundEvent _sound${index})
	_sound${index}.setSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("${sound}"))));