<#if input$x?has_content && input$y?has_content && input$z?has_content && input$tagName?has_content>
${package}.api.render.BEWRLStorage.getBlockBEWRL(world, BlockPos.containing(${input$x}, ${input$y}, ${input$z}), ${input$tagName})
<#else>null</#if>
