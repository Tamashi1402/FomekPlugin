<#if statement$sort != "" && statement$foreach != "">
  {
    TreeMap<Double, Tag> _treeMap = new TreeMap<>(
      <#if field$order == "DESCENDING">
        Comparator.reverseOrder()
      </#if>
    );
    for (Tag dataelementiterator : ${input$list}) {
      double sortkey = 0.0D;
      ${statement$sort}
      _treeMap.put(sortkey, dataelementiterator);
    }
    for (Tag dataelementiterator : _treeMap.values()) {
      ${statement$foreach}
    }
  }
</#if>
