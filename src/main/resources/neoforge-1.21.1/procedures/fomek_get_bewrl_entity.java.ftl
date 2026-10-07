<#if input$entity?has_content && input$tagName?has_content>
${package}.api.render.BEWRLStorage.getEntityBEWRL(${input$entity}, ${input$tagName})
<#else>null</#if>
