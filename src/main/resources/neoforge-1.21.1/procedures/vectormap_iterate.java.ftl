for (Map.Entry<String, Vec3> _vectorMapEntry : ${input$map}.entrySet()) {
  String keyiterator = _vectorMapEntry.getKey();
  Vec3 vectoriterator = _vectorMapEntry.getValue();
  ${statement$foreach}
}