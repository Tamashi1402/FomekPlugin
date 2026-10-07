<#if field$entity_type == "non_mob">
!((${input$entity} instanceof Mob) || (${input$entity} instanceof Player))
<#elseif field$entity_type == "normal_zombie">
((${input$entity} instanceof Zombie) && !(${input$entity} instanceof ZombifiedPiglin))
<#elseif field$entity_type == "all_zombies">
((${input$entity} instanceof Zombie) || (${input$entity} instanceof ZombieHorse) || (${input$entity} instanceof Zoglin))
<#elseif field$entity_type == "aquatic_creature">
((${input$entity} instanceof WaterAnimal) || (${input$entity} instanceof Guardian) || (${input$entity} instanceof Axolotl) || (${input$entity} instanceof Turtle))
<#elseif field$entity_type == "animal">
(!(${input$entity} instanceof AbstractGolem) && ((${input$entity} instanceof Animal) || (${input$entity} instanceof Bat) || (${input$entity} instanceof Axolotl)))
<#elseif field$entity_type == "pseudo_tamable">
((${input$entity} instanceof Ocelot) || (${input$entity} instanceof Fox) || (${input$entity} instanceof Axolotl) || (${input$entity} instanceof AbstractHorse))
<#elseif field$entity_type == "boat_without_chest">
((${input$entity} instanceof Boat) && !(${input$entity} instanceof ChestBoat))
<#else>
(${input$entity} instanceof ${field$entity_type})
</#if>


