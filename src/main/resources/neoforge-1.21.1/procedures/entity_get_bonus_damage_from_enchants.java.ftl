<#include "mcitems.ftl">
(EnchantmentHelper.getDamageBonus(${(input$item)}, (${input$entity} instanceof LivingEntity _livEnt ? _livEnt.getMobType() : null)))