<#include "redwires_plugin_utils.ftl">
final int _level${index} = ${opt.toInt(input$enchantmentlvl)};
EnchantmentHelper.updateEnchantments(_redwire$itemstack, _enchantment${index} -> _enchantment${index}.set(_enchantHolder, _level${index}));