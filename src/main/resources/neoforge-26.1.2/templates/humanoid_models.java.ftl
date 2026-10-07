package ${package}.init;

@EventBusSubscriber(Dist.CLIENT)
public class ${JavaModName}HumanoidModels {
    <#list humanoidmodels as model>
        public static PlayerModel ${model.getModElement().getRegistryNameUpper()};
    </#list>

    @SubscribeEvent
    public static void initModels(EntityRenderersEvent.AddLayers event) {
        <#list humanoidmodels as model>
            <#assign modelname = model.getModElement().getRegistryName() + '_temp'>
            ${model.modelName} ${modelname} = new ${model.modelName}(Minecraft.getInstance().getEntityModels().bakeLayer(${model.modelName}.LAYER_LOCATION));
            ${model.getModElement().getRegistryNameUpper()} = new PlayerModel(new ModelPart(Collections.emptyList(), Map.ofEntries(
                Map.entry("hat", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                <#if model.helmetModelPart != "Empty">
                   Map.entry("head", ${modelname}.${model.helmetModelPart}),
                <#else>
                   Map.entry("head", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                <#if model.bodyModelPart != "Empty">
                    Map.entry("body", ${modelname}.${model.bodyModelPart}),
                <#else>
                    Map.entry("body", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                <#if model.armsModelPartL != "Empty">
                    Map.entry("left_arm", ${modelname}.${model.armsModelPartL}),
                <#else>
                    Map.entry("left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                <#if model.armsModelPartR != "Empty">
                    Map.entry("right_arm", ${modelname}.${model.armsModelPartR}),
                <#else>
                    Map.entry("right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                <#if model.leggingsModelPartL != "Empty">
                    Map.entry("left_leg", ${modelname}.${model.leggingsModelPartL}),
                <#else>
                    Map.entry("left_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                <#if model.leggingsModelPartR != "Empty">
                    Map.entry("right_leg", ${modelname}.${model.leggingsModelPartR}),
                <#else>
                    Map.entry("right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                </#if>
                Map.entry("left_sleeve", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("right_sleeve", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("left_pants", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("right_pants", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("jacket", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("cloak", new ModelPart(Collections.emptyList(), Collections.emptyMap())),
                Map.entry("ear", new ModelPart(Collections.emptyList(), Collections.emptyMap()))
            )), false);
        </#list>
    }
}