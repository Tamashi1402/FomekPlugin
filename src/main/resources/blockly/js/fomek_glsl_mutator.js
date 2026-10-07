// guarded local copy: MCreator concatenates all plugin blockly js files into ONE
// script and executes it once. Load order is arbitrary (hash set), so files
// must never depend on globals defined in another file. See fomekmenu.js.
if (typeof simpleRepeatingInputMixin === 'undefined') {
    var simpleRepeatingInputMixin = function(containerType, inputBlockType, repeatingInput, addInputFn) {
    function isMutatorInput(name) {
        if (name === repeatingInput) return true;
        if (name.startsWith(repeatingInput) && /^\d+$/.test(name.substring(repeatingInput.length))) return true;
        return false;
    }

    function getAlignRight() {
        return Blockly.ALIGN_RIGHT ?? (Blockly.Input && Blockly.Input.Align && Blockly.Input.Align.RIGHT);
    }

    return {
        mutationToDom: function() {
            var container = document.createElement('mutation');
            var count = 0;
            for (var i = 0; i < this.inputList.length; i++) {
                if (isMutatorInput(this.inputList[i].name)) count++;
            }
            container.setAttribute('inputs', count);
            container.setAttribute('count', count);
            return container;
        },

        domToMutation: function(element) {
            var count = parseInt(element.getAttribute('count') || '0', 10);

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMutatorInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate inputs from saved count
            for (var i = 0; i < count; i++) {
                addInputFn(this, repeatingInput, i);
            }
        },

        decompose: function(workspace) {
            var containerBlock = workspace.newBlock(containerType);
            containerBlock.initSvg();
            var connection = containerBlock.getInput('STACK').connection;

            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMutatorInput(input.name)) {
                    var inputBlock = workspace.newBlock(inputBlockType);
                    inputBlock.initSvg();
                    connection.connect(inputBlock.previousConnection);
                    connection = inputBlock.nextConnection;
                }
            }
            return containerBlock;
        },

        compose: function(containerBlock) {
            // Save connected blocks before removing inputs
            var savedConnections = {};
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isRepeatingInput(input.name) && input.connection && input.connection.targetConnection) {
                    savedConnections[input.name] = input.connection.targetConnection;
                }
            }

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isRepeatingInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Read mutator workspace and create inputs
            var block = containerBlock.getInputTargetBlock('STACK');
            var index = 0;
            while (block) {
                var inputName = repeatingInput + index;
                addInputFn(this, inputName, index);
                // Restore saved connection if this input existed before
                if (savedConnections[inputName]) {
                    var inp = this.getInput(inputName);
                    if (inp && inp.connection) {
                        inp.connection.connect(savedConnections[inputName]);
                    }
                }
                index++;
                block = block.getNextBlock();
            }
        }
    };
};
}

Blockly.Blocks['fomek_glsl_lines_container'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_glsl_lines_mutator.container'));
        this.appendStatementInput('STACK');
        this.contextMenu = false;
        this.setColour(55);
    }
};

Blockly.Blocks['fomek_glsl_lines_input'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_glsl_lines_mutator.input'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(55);
    }
};

Blockly.Extensions.registerMutator('fomek_glsl_lines_mutator',
    simpleRepeatingInputMixin(
        'fomek_glsl_lines_container',
        'fomek_glsl_lines_input',
        'glsllines',
        function(thisBlock, inputName, index) {
            const ALIGN_RIGHT = Blockly.ALIGN_RIGHT ?? Blockly.Input.Align.RIGHT;
            thisBlock.appendValueInput(inputName + index)
                .setCheck('String')
                .setAlign(ALIGN_RIGHT)
                .appendField('line ' + (index + 1) + ':');
        }
    ),
    undefined,
    ['fomek_glsl_lines_input']
);