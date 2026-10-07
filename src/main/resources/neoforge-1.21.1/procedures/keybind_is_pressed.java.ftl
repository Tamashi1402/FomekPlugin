new Object() {
  public boolean getValue() {
    boolean retBool = Minecraft.getInstance().options.${field$keybind}.isDown();
    if (retBool) {
      if (Minecraft.getInstance().options.${field$keybind}.getKeyModifier().toString().equals("SHIFT")) {
        retBool = Screen.hasShiftDown();
      } else if (Minecraft.getInstance().options.${field$keybind}.getKeyModifier().toString().equals("CONTROL")) {
        retBool = Screen.hasControlDown();
      } else if (Minecraft.getInstance().options.${field$keybind}.getKeyModifier().toString().equals("ALT")) {
        retBool = Screen.hasAltDown();
      }
    }
    return retBool;
  }
}.getValue()