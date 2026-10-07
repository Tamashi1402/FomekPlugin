<#include "mcelements.ftl">
if (world instanceof ServerLevel _serverLvl) {
	StructureTemplate _template = _serverLvl.getStructureManager().getOrCreate(new ResourceLocation("${modid}", "${field$schematic}"));
	if (_template != null) {
    Rotation _rotate = Rotation.NONE;
    if (${input$rotation} == Direction.NORTH) {
      _rotate = Rotation.CLOCKWISE_180;
    } else if (${input$rotation} == Direction.SOUTH) {
      _rotate = Rotation.NONE;
    } else if (${input$rotation} == Direction.EAST) {
      _rotate = Rotation.COUNTERCLOCKWISE_90;
    } else if (${input$rotation} == Direction.WEST) {
      _rotate = Rotation.CLOCKWISE_90;
    }
  
		_template.placeInWorld(_serverLvl,
    ${toBlockPos(input$x,input$y,input$z)},
    ${toBlockPos(input$x,input$y,input$z)},
    new StructurePlaceSettings()

    .setRotation(_rotate)
    .setMirror(Mirror.${field$mirror})
    .setIgnoreEntities(false), _serverLvl.random, 3);
	}
}