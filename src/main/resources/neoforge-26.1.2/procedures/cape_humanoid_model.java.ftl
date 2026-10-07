<#assign humanoid = generator.map(field$model, "humanoidmodels")?replace("CUSTOM:", "")>
<#assign part = generator.map(field$modelpart, "humanoidparts")>
{
    capes.add(() -> {
        Player player = (Player) entity;
        float partialTicks = playerRenderEvent.getPartialTick();
        ${humanoid}.crouching = player.isShiftKeyDown();
        if (!player.isFallFlying() && player.isAlive()) {
            double dx = player.xCloakO + (player.xCloak - player.xCloakO) * partialTicks
                      - (player.xOld + (player.getX() - player.xOld) * partialTicks);
            double dy = player.yCloakO + (player.yCloak - player.yCloakO) * partialTicks
                      - (player.yOld + (player.getY() - player.yOld) * partialTicks);
            double dz = player.zCloakO + (player.zCloak - player.zCloakO) * partialTicks
                      - (player.zOld + (player.getZ() - player.zOld) * partialTicks);

            float bodyYaw = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTicks;
            double sin = Math.sin(bodyYaw * Math.PI / 180.0);
            double cos = -Math.cos(bodyYaw * Math.PI / 180.0);

            float xRot = (float) (dy * 10.0);
            xRot = Math.max(xRot, -6.0F);
            xRot = Math.min(xRot, 32.0F);

            float yRot = (float) ((dx * sin + dz * cos) * 100.0F);
            float zRot = (float) ((dx * cos - dz * sin) * 100.0F);

            yRot = Math.max(yRot, 0.0F);
            yRot = Math.min(yRot, 150.0F);
            zRot = Math.max(zRot, -20.0F);
            zRot = Math.min(zRot, 20.0F);

            if (xRot < -5.0F) xRot = -5.0F;

            ${humanoid}.${part}.xRot = Math.min((float)Math.toRadians(6.0F + xRot / 2.0F + yRot / 2.0F) + ${humanoid}.${part}.xRot, ${opt.toFloat(input$maxRotation)});
            ${humanoid}.${part}.yRot = 0.0F;
            ${humanoid}.${part}.zRot = (float)Math.toRadians(zRot / 2.0F);
        } else {
            ${humanoid}.${part}.xRot = 0.0F;
            ${humanoid}.${part}.yRot = 0.0F;
            ${humanoid}.${part}.zRot = 0.0F;
        }
    });
}