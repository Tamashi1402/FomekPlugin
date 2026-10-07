CompoundTag _index${cbi} = new CompoundTag();
${input$entity}.saveWithoutId(_index${cbi});
_index${cbi}.putBoolean("powered", ${input$boolean});
${input$entity}.load(_index${cbi});