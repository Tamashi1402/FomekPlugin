Blockly.Msg.DATAELEMENT_HUE = 330;
Blockly.Msg.DATALIST_HUE = 35;
Blockly.Msg.DATAMAP_HUE = 100;
Blockly.Msg.RANDOMSOURCE_HUE = 300;
Blockly.Msg.VECTOR_HUE = 175;
Blockly.Msg.VECTORLIST_HUE = 195;
Blockly.Msg.VECTORMAP_HUE = 280;

function getLocalVariablesOfType(variableType) {
  let allVariables = getVariablesOfType(variableType);
  let localVariables = [];
  if (Array.isArray(allVariables)) {
    allVariables.forEach(e => {
      if (Array.isArray(e) && e.length == 2 && new String(e[1]).indexOf('local:') == 0)
        localVariables.push(e);
    });
  }
  if (localVariables.length == 0)
    localVariables.push(['', '']);
  return localVariables;
}

Blockly.Extensions.register(
  'mathutils_dataelement_variables',
  function () {
    this.getInput('dataelementvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('DataElement')), 'DATAELEMENT_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_datalist_variables',
  function () {
    this.getInput('datalistvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('DataList')), 'DATALIST_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_datamap_variables',
  function () {
    this.getInput('datamapvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('DataMap')), 'DATAMAP_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_string_variables',
  function () {
    this.getInput('stringvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('String')), 'STRING_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_vector_variables',
  function () {
    this.getInput('vectorvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('Vector')), 'VECTOR_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_vectorlist_variables',
  function () {
    this.getInput('vectorlistvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('VectorList')), 'VECTORLIST_VAR');
  }
);

Blockly.Extensions.register(
  'mathutils_vectormap_variables',
  function () {
    this.getInput('vectormapvar').appendField(new Blockly.FieldDropdown(getLocalVariablesOfType('VectorMap')), 'VECTORMAP_VAR');
  }
);