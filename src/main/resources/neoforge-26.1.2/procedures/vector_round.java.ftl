<#if field$operator == "CEIL">
  ((new Function<Vec3, Vec3>() {
    @Override
    public Vec3 apply(Vec3 vec3) {
      return new Vec3(Math.ceil(vec3.x()), Math.ceil(vec3.y()), Math.ceil(vec3.z()));
    }
  }).apply(${input$vector}))
<#elseif field$operator == "FLOOR">
  ((new Function<Vec3, Vec3>() {
    @Override
    public Vec3 apply(Vec3 vec3) {
      return new Vec3(Math.floor(vec3.x()), Math.floor(vec3.y()), Math.floor(vec3.z()));
    }
  }).apply(${input$vector}))
<#elseif field$operator == "ROUND">
  ((new Function<Vec3, Vec3>() {
    @Override
    public Vec3 apply(Vec3 vec3) {
      return new Vec3(Math.round(vec3.x()), Math.round(vec3.y()), Math.round(vec3.z()));
    }
  }).apply(${input$vector}))
</#if>