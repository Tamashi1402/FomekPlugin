((new Object() {
  public double get(Vec3 vec3) {
    return Math.toDegrees(Math.acos(vec3.multiply(1.0D, 0.0D, 1.0D).normalize().z())) * (vec3.x() >= 0.0D ? -1.0D : 1.0D);
  }
}).get(${input$vector}))