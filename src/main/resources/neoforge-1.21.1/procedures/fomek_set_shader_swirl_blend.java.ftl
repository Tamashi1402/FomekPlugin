<#if field$blendMode == "GLOBAL">
${package}.api.render.RenderAPI.setShaderSwirlBlendMode((${package}.api.render.Shader) ${input$shader}, null);
<#else>
${package}.api.render.RenderAPI.setShaderSwirlBlendMode((${package}.api.render.Shader) ${input$shader}, "${field$blendMode}");
</#if>