{
    ResourceLocation texture = ${input$texture};
    PlayerModel humanoidModel = ${generator.map(field$model, "humanoidmodels")?replace("CUSTOM:", "")};
    PlayerModel playerOriginal = (PlayerModel) entityModel;
    boolean lefthanded = armRenderEvent.getArm() == HumanoidArm.LEFT;
    ModelPart part = lefthanded ? humanoidModel.leftArm : humanoidModel.rightArm;
    boolean partVisible = part.visible;
    part.resetPose();
    if (lefthanded) playerOriginal.leftArm.resetPose(); else playerOriginal.rightArm.resetPose();
    part.copyFrom(lefthanded ? playerOriginal.leftArm : playerOriginal.rightArm);
    part.visible = true;
	part.zRot = 0.1f;
    part.xRot = -0.005f;
    part.y = 2;
    part.render(poseStack, armRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})), armRenderEvent.getPackedLight(), OverlayTexture.NO_OVERLAY);
    part.visible = partVisible;
}