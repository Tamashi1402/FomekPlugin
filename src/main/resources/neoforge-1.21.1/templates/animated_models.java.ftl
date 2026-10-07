package ${package}.init;

<#include "procedures.java.ftl">

@EventBusSubscriber(Dist.CLIENT)
public class ${JavaModName}AnimatedModels {
	<#list animatedmodels as model>
		public static EntityModel ${model.getModElement().getRegistryNameUpper()};
	</#list>
	private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();

	@SubscribeEvent
	public static void initModels(EntityRenderersEvent.AddLayers event) {
		<#list animatedmodels as model>
		${model.getModElement().getRegistryNameUpper()} = create${model.modelName}();
		</#list>
	}

	<#list animatedmodels as model>
	private static EntityModel create${model.modelName}() {
		ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(${model.modelName}.LAYER_LOCATION);
		class Animatable${model.modelName} extends ${model.modelName} implements Animatable {
			Animatable${model.modelName}() { super(root); }

			private final HierarchicalModel<Entity> animator = new HierarchicalModel<Entity>() {
				private final Map<Entity, Map<Integer, Long>> animationStartTimes = new WeakHashMap<>();

				@Override
				public ModelPart root() {
					return root;
				}

				@Override
				public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount,
									  float ageInTicks, float netHeadYaw, float headPitch) {
					this.root().getAllParts().forEach(ModelPart::resetPose);
					long time = (long) (ageInTicks * 50.0F);

					Map<Integer, Long> entityStartTimes = animationStartTimes.computeIfAbsent(entity, e -> new HashMap<>());

					<#list model.animations as animation>
					<#if !animation.walking>
					<#if hasProcedure(animation.condition)>
					if (<@procedureCode animation.condition, {
					   "x": "entity.getX()",
					   "y": "entity.getY()",
					   "z": "entity.getZ()",
					   "entity": "entity",
					   "world": "entity.level()"
					}, false/>) {
						long startTime${animation?index} = entityStartTimes.computeIfAbsent(${animation?index}, k -> time);
						KeyframeAnimations.animate(this, ${animation.animation}, time - startTime${animation?index}, ${animation.speed}f, ANIMATION_VECTOR_CACHE);
					} else {
						entityStartTimes.remove(${animation?index});
					}
					<#else>
					long startTime${animation?index} = entityStartTimes.computeIfAbsent(${animation?index}, k -> time);
					KeyframeAnimations.animate(this, ${animation.animation}, time - startTime${animation?index}, ${animation.speed}f, ANIMATION_VECTOR_CACHE);
					</#if>
					<#else>
					<#if hasProcedure(animation.condition)>
					if (<@procedureCode animation.condition, {
					   "x": "entity.getX()",
					   "y": "entity.getY()",
					   "z": "entity.getZ()",
					   "entity": "entity",
					   "world": "entity.level()"
					}, false/>) {
						this.animateWalk(${animation.animation}, limbSwing, limbSwingAmount, ${animation.speed}f, ${animation.amplitude}f);
					}
					<#else>
					this.animateWalk(${animation.animation}, limbSwing, limbSwingAmount, ${animation.speed}f, ${animation.amplitude}f);
					</#if>
					</#if>
					</#list>
				}
			};

			@Override
			public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount,
								  float ageInTicks, float netHeadYaw, float headPitch) {
				animator.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
				super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			}

			@Override
			public ModelPart getRoot() {
				return root;
			}
		}
		return new Animatable${model.modelName}();
	}
	</#list>

	public static interface Animatable {
		ModelPart getRoot();

		default void applyPlayerRotations(PlayerModel playerModel) {
			ModelPart root = getRoot();

			Map<String, ModelPart> playerParts = Map.of(
				"head",      playerModel.head,
				"body",      playerModel.body,
				"right_arm", playerModel.rightArm,
				"left_arm",  playerModel.leftArm,
				"right_leg", playerModel.rightLeg,
				"left_leg",  playerModel.leftLeg
			);

			playerParts.forEach((name, playerPart) -> {
				try {
					ModelPart myPart = root.getChild(name);
					myPart.copyFrom(playerPart);
				} catch (NoSuchElementException ignored) {
					// this model simply doesn't have a part by that name
				}
			});
		}
	}
}