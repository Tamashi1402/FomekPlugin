<#if field$mode == "TEXTURE">
if (${package}.api.render.RenderAPI.beginShape(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, true, ${input$update})) {
<#elseif field$mode == "TEXTURE_TRIANGLES">
if (${package}.api.render.RenderAPI.beginShape(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, true, ${input$update})) {
<#else>
if (${package}.api.render.RenderAPI.beginShape(com.mojang.blaze3d.vertex.VertexFormat.Mode.${field$mode}, false, ${input$update})) {
</#if>
  ${statement$do}
  ${package}.api.render.RenderAPI.endShape();
}
