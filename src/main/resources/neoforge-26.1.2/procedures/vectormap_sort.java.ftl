<#if statement$sort != "" && statement$foreach != "">
  {
    TreeMap<Double, Map.Entry<String, Vec3>> _treeMap = new TreeMap<>(
      <#if field$order == "DESCENDING">
        Comparator.reverseOrder()
      </#if>
    );
    for (Map.Entry<String, Vec3> _mapEntry : ${input$map}.entrySet()) {
      double sortkey = 0.0D;
      String keyiterator = _mapEntry.getKey();
      Vec3 vectoriterator = _mapEntry.getValue();
      ${statement$sort}
      _treeMap.put(sortkey, _mapEntry);
    }
    for (Map.Entry<Double, Map.Entry<String, Vec3>> _treeMapEntry : _treeMap.entrySet()) {
      String keyiterator = _treeMapEntry.getValue().getKey();
      Vec3 vectoriterator = _treeMapEntry.getValue().getValue();
      ${statement$foreach}
    }
  }
</#if>
