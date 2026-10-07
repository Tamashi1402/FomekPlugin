<#if statement$sort != "" && statement$foreach != "">
  {
    TreeMap<Double, String> _treeMap = new TreeMap<>(
      <#if field$order == "DESCENDING">
        Comparator.reverseOrder()
      </#if>
    );
    for (String keyiterator : ${input$map}.getAllKeys()) {
      double sortkey = 0.0D;
      ${statement$sort}
      _treeMap.put(sortkey, keyiterator);
    }
    for (String keyiterator : _treeMap.values()) {
      ${statement$foreach}
    }
  }
</#if>
