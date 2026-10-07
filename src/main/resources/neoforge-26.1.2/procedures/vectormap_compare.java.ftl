<#if field$operator == "=">
  ((new Object() {
    public boolean is(HashMap<String, Vec3> left, HashMap<String, Vec3> right) {
      return left.keySet().equals(right.keySet()) && left.values().equals(right.values());
    }
  }).is(${input$left}, ${input$right}))
<#elseif field$operator == "≠">
  (!(new Object() {
    public boolean is(HashMap<String, Vec3> left, HashMap<String, Vec3> right) {
      return left.keySet().equals(right.keySet()) && left.values().equals(right.values());
    }
  }).is(${input$left}, ${input$right}))
</#if>