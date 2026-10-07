<#if field$operator == "+">
  (${input$left}.add(${input$right}))
<#elseif field$operator == "-">
  (${input$left}.subtract(${input$right}))
<#elseif field$operator == "*">
  (${input$left}.multiply(${input$right}))
<#elseif field$operator == "/">
  ((new BiFunction<Vec3, Vec3, Vec3>() {
    @Override
    public Vec3 apply(Vec3 left, Vec3 right) {
      return new Vec3(left.x() / right.x(), left.y() / right.y(), left.z() / right.z());
    }
  }).apply(${input$left}, ${input$right}))
</#if>