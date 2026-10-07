<#if statement$sort != "" && statement$foreach != "">
  {
    TreeMap<Double, Vec3> _treeMap = new TreeMap<>(
      <#if field$order == "DESCENDING">
        Comparator.reverseOrder()
      </#if>
    );
    for (Vec3 vectoriterator : ${input$list}) {
      double sortkey = 0.0D;
      ${statement$sort}
      _treeMap.put(sortkey, vectoriterator);
    }
    for (Vec3 vectoriterator : _treeMap.values()) {
      ${statement$foreach}
    }
  }
</#if>
