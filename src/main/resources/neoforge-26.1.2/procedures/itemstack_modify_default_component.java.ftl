<#include "mcitems.ftl">
<#assign datacomp = generator.map(field$datacomponents, "datacomponents")>
<#assign item = mappedMCItemToItem(input$provideditemstack)>
<#assign value = input$val>
<#include "redwires_plugin_utils.ftl">
if (event instanceof ModifyDefaultComponentsEvent _modComponent${index}) {
	<#if datacomp == "DataComponents.MAX_DAMAGE" || datacomp == "DataComponents.DAMAGE" || datacomp?contains("MAX_STACK_SIZE") || datacomp?contains("REPAIR_COST")>
	final int _value = ${opt.toInt(value)};
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, _value));
	<#elseif datacomp?contains("NAME")>
	final Component _value = Component.literal(${value});
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, _value));
	<#elseif datacomp?contains("USE_COOLDOWN")>
	final UseCooldown _value = new UseCooldown(${opt.toFloat(value)});
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, _value));
	<#elseif datacomp?contains("NOTE_BLOCK_SOUND") || datacomp?contains("TOOLTIP_STYLE")>
	final ResourceLocation _value = ResourceLocation.parse(${value});
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, _value));
	<#elseif datacomp?contains("ITEM_MODEL")>
	final ResourceLocation _value = BuiltInRegistries.ITEM.getKey(${mappedMCItemToItem(value)});
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, _value));
	<#else>
    _modComponent${index}.modify(${item}, builder -> builder.set(${datacomp}, ${value}));
	</#if>
}