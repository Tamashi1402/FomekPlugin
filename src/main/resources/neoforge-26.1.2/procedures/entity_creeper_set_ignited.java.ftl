CompoundTag dataIndex${cbi} = new CompoundTag();
${input$entity}.saveWithoutId(dataIndex${cbi});
dataIndex${cbi}.putBoolean("ignited", ${input$boolean});
${input$entity}.load(dataIndex${cbi});