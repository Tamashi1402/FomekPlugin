class FieldDisplay extends Blockly.FieldTextInput {
  maxDisplayLength = 9999;
}
Blockly.fieldRegistry.register('field_display', FieldDisplay);