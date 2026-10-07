package ${package}.client;

import net.minecraft.client.gui.components.Button;

<#include "../procedures.java.ftl">

<#assign elements = invedits>

<#assign hasCurios = settings.getMCreatorDependenciesRaw()?seq_contains("curios_api")>

<#assign labels = []>
<#assign labeltypes = []>
<#assign images = []>
<#assign imagetypes = []>
<#assign sprites = []>
<#assign spritetypes = []>
<#assign buttons = []>
<#assign buttontypes = []>
<#assign imagebuttons = []>
<#assign imagebuttontypes = []>
<#assign tooltips = []>
<#assign tooltiptypes = []>
<#assign entitymodels = []>
<#assign entitymodeltypes = []>

<#list elements?filter(e -> e.getComponentsOfType("Label")?size != 0) as edit>
	<#list edit.getComponentsOfType("Label") as item>
		<#assign labels += [item]>
		<#assign labeltypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("Image")?size != 0) as edit>
	<#list edit.getComponentsOfType("Image") as item>
		<#assign images += [item]>
		<#assign imagetypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("Sprite")?size != 0) as edit>
	<#list edit.getComponentsOfType("Sprite") as item>
		<#assign sprites += [item]>
		<#assign spritetypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("Button")?size != 0) as edit>
	<#list edit.getComponentsOfType("Button") as btn>
		<#assign buttons += [btn]>
		<#assign buttontypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("ImageButton")?size != 0) as edit>
	<#list edit.getComponentsOfType("ImageButton") as item>
		<#assign imagebuttons += [item]>
		<#assign imagebuttontypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("Tooltip")?size != 0) as edit>
	<#list edit.getComponentsOfType("Tooltip") as item>
		<#assign tooltips += [item]>
		<#assign tooltiptypes += [edit.type]>
	</#list>
</#list>

<#list elements?filter(e -> e.getComponentsOfType("EntityModel")?size != 0) as edit>
	<#list edit.getComponentsOfType("EntityModel") as item>
		<#assign entitymodels += [item]>
		<#assign entitymodeltypes += [edit.type]>
	</#list>
</#list>

@EventBusSubscriber(Dist.CLIENT)
public class InventoryRenderer {
	private static Minecraft mc = Minecraft.getInstance();
	private static EffectRenderingInventoryScreen screen = null;

	<#list images as component>
		private static final ResourceLocation IMAGE_${component?index} = ResourceLocation.parse("${modid}:textures/screens/${component.image}");
	</#list>

	<#list sprites as component>
		private static final ResourceLocation SPRITE_${component?index} = ResourceLocation.parse("${modid}:textures/screens/${component.sprite}");
	</#list>

	<#if buttons?size != 0 || imagebuttons?size != 0>
		private static final List<AbstractWidget> buttons = new ArrayList<>();
	</#if>

	@SubscribeEvent
	public static void openScreen(ScreenEvent.Init.Pre event) {
		if (event.getScreen() instanceof EffectRenderingInventoryScreen inventory) {
			if (mc == null) mc = Minecraft.getInstance();
			screen = inventory;
			<#if buttons?size != 0 || imagebuttons?size != 0>initButtons(event);</#if>
		}
	}

	<#if buttons?size != 0 || imagebuttons?size != 0>
		@SubscribeEvent
		public static void closeScreen(ScreenEvent.Closing event) {
		    if (mc == null) mc = Minecraft.getInstance();
		    if (mc != null) {
		        if (mc.gameMode == null) {
		            buttons.clear();
		            return;
		        }
		    }
			if (<#if hasCurios>((</#if>event.getScreen() instanceof InventoryScreen<#if hasCurios>) || event.getScreen().getClass().getSimpleName().equals("CuriosScreen"))</#if> && !mc.gameMode.hasInfiniteItems())
				buttons.clear();
			if (event.getScreen() instanceof CreativeModeInventoryScreen)
				buttons.clear();
		}
	</#if>

	@SubscribeEvent
	public static void renderBackground(ContainerScreenEvent.Render.Background event) {
		if (!(event.getContainerScreen() instanceof EffectRenderingInventoryScreen)) return;
		if (mc == null) mc = Minecraft.getInstance();
		if (screen == null)
			screen = (EffectRenderingInventoryScreen) event.getContainerScreen();
		<#if buttons?size != 0 || imagebuttons?size != 0>
			boolean flag = getFlag();
			<#assign btid = 0>
			if (!buttons.isEmpty()) {
			<#list buttons as component>
				buttons.get(${btid}).visible = flag<#if hasProcedure(component.displayCondition)>&& <@valueProvider component.displayCondition/></#if>;
				<#assign btid += 1>
			</#list>
			<#list imagebuttons as component>
				buttons.get(${btid}).visible = flag<#if hasProcedure(component.displayCondition)>&& <@valueProvider component.displayCondition/></#if>;
				<#assign btid += 1>
			</#list>
			}
		</#if>
		<#if sprites?size != 0 || images?size != 0>renderImages(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());</#if>
	}

	@SubscribeEvent
	public static void renderForeground(ContainerScreenEvent.Render.Foreground event) {
		if (mc == null) mc = Minecraft.getInstance();
		if (!(event.getContainerScreen() instanceof EffectRenderingInventoryScreen)) return;
		if (screen == null)
			screen = (EffectRenderingInventoryScreen) event.getContainerScreen();

		<#if labels?size != 0>renderLabels(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());</#if>
		<#if entitymodels?size != 0>renderEntityModels(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());</#if>
		<#if tooltips?size != 0>renderTooltips(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());</#if>
	}

	<#if buttons?size != 0 || imagebuttons?size != 0>
	private static void initButtons(ScreenEvent.Init.Pre event) {
		<#assign btid = 0>
		<#list buttons as component>
			<#if component.isUndecorated>
				buttons.add(new PlainTextButton(
						screen.getGuiLeft() + ${component.gx(getWidth(buttontypes, component?index)) + 125 - (buttontypes[component?index] == 1)?then(9, 0)}, screen.getGuiTop() + ${component.gy(getHeight(buttontypes, component?index)) + 37 + (buttontypes[component?index] == 1)?then(15, 0)},
						${component.width}, ${component.height},
						Component.translatable("invedit.${modid}.${component.getName()}"),
						<@buttonOnClick component/>, mc.font
					) {
						@Override public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
							this.setX(screen.getGuiLeft() + ${component.gx(getWidth(buttontypes, component?index))});
							this.setY(screen.getGuiTop() + ${component.gy(getHeight(buttontypes, component?index))});
							super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
						}
					});
			<#else>
				buttons.add(new Button(Button.builder(Component.translatable("invedit.${modid}.${component.getName()}"), <@buttonOnClick component/>)
						.bounds(screen.getGuiLeft() + ${component.gx(getWidth(buttontypes, component?index)) + 125 - (buttontypes[component?index] == 1)?then(9, 0)},
						screen.getGuiTop() + ${component.gy(getHeight(buttontypes, component?index)) + 37 + (buttontypes[component?index] == 1)?then(15, 0)}, ${component.width}, ${component.height})
					) {
						@Override public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
							this.setX(screen.getGuiLeft() + ${component.gx(getWidth(buttontypes, component?index))});
							this.setY(screen.getGuiTop() + ${component.gy(getHeight(buttontypes, component?index))});
							super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
						}
					});
			</#if>
			<#if buttontypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen)
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			event.addListener(buttons.get(${btid}));
			<#assign btid += 1>
		</#list>
		<#list imagebuttons as component>
			buttons.add(new ImageButton(
				screen.getGuiLeft() + ${component.gx(getWidth(imagebuttontypes, component?index)) + 125 - (imagebuttontypes[component?index] == 1)?then(9, 0)}, screen.getGuiTop() + ${component.gy(getHeight(imagebuttontypes, component?index)) + 37 + (imagebuttontypes[component?index] == 1)?then(15, 0)},
				${component.getWidth(w.getWorkspace())}, ${component.getHeight(w.getWorkspace())},
				<#if component.hoveredImage?has_content>
				new WidgetSprites(ResourceLocation.parse("${modid}:textures/screens/${component.image}"), ResourceLocation.parse("${modid}:textures/screens/${component.hoveredImage}")),
				<#else>
				new WidgetSprites(ResourceLocation.parse("${modid}:textures/screens/${component.image}"), ResourceLocation.parse("${modid}:textures/screens/${component.image}")),
				</#if>
				<@buttonOnClick component/>
			) {
				@Override public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
					this.setX(screen.getGuiLeft() + ${component.gx(getWidth(imagebuttontypes, component?index))});
					this.setY(screen.getGuiTop() + ${component.gy(getHeight(imagebuttontypes, component?index))});
					if (getFlag()<#if hasProcedure(component.displayCondition)> && <@valueProvider component.displayCondition/></#if>)
						guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, 0, width, height, width, height);
				}
			});
			<#if imagebuttontypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen)
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			event.addListener(buttons.get(${btid}));
			<#assign btid += 1>
		</#list>
	}
	</#if>

	<#if sprites?size != 0 || images?size != 0>
	private static void renderImages(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		<#list images as component>
			<#if imagetypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen inventory && inventory.isInventoryOpen())
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			<#if hasProcedure(component.displayCondition)>if (<@valueProvider component.displayCondition/>) {</#if>
				guiGraphics.blit(IMAGE_${component?index},
					screen.getGuiLeft() + ${component.gx(getWidth(imagetypes, component?index))}, screen.getGuiTop() + ${component.gy(getHeight(imagetypes, component?index))}, 0, 0,
					${component.getWidth(w.getWorkspace())}, ${component.getHeight(w.getWorkspace())},
					${component.getWidth(w.getWorkspace())}, ${component.getHeight(w.getWorkspace())});
			<#if hasProcedure(component.displayCondition)>}</#if>
		</#list>
		<#list sprites as component>
			<#if spritetypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen inventory && inventory.isInventoryOpen())
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			<#if hasProcedure(component.displayCondition)>if (<@valueProvider component.displayCondition/>) {</#if>
				guiGraphics.blit(SPRITE_${component?index},
					screen.getGuiLeft() + ${component.gx(getWidth(spritetypes, component?index))}, screen.getGuiTop() + ${component.gy(getHeight(spritetypes, component?index))},
					<#if (component.getTextureWidth(w.getWorkspace()) > component.getTextureHeight(w.getWorkspace()))>
						<@getSpriteByIndex component "width"/>, 0
					<#else>
						0, <@getSpriteByIndex component "height"/>
					</#if>,
					${component.getWidth(w.getWorkspace())}, ${component.getHeight(w.getWorkspace())},
					${component.getTextureWidth(w.getWorkspace())}, ${component.getTextureHeight(w.getWorkspace())});
			<#if hasProcedure(component.displayCondition)>}</#if>
		</#list>
	}
	</#if>

	<#if labels?size != 0>
	private static void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		<#list labels as component>
			<#if labeltypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen inventory && inventory.isInventoryOpen())
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			<#if hasProcedure(component.displayCondition)>
				if (<@valueProvider component.displayCondition/>)
			</#if>
			guiGraphics.drawString(mc.font,
				<#if hasProcedure(component.text)><@valueProvider component.text/><#else>Component.translatable("invedit.${modid}.${component.getName()}")</#if>,
				${component.gx(getWidth(labeltypes, component?index))}, ${component.gy(getHeight(labeltypes, component?index))}, ${component.color.getRGB()}, ${component.hasShadow});
		</#list>
	}
	</#if>

	<#if entitymodels?size != 0>
	private static void renderEntityModels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		<#list entitymodels as component>
			<#assign followMouse = component.followMouseMovement>
			<#assign x = component.gx(getWidth(entitymodeltypes, component?index))>
			<#assign y = component.gy(getHeight(entitymodeltypes, component?index))>
			<#if entitymodeltypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen inventory && inventory.isInventoryOpen())
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			if (<@valueProvider component.entityModel/> instanceof LivingEntity livingEntity) {
				<#if hasProcedure(component.displayCondition)>
					if (<@valueProvider component.displayCondition/>)
				</#if>
				renderEntityInInventoryFollowsAngle(guiGraphics, ${x + 10}, ${y + 20}, ${component.scale},
					${component.rotationX / 20.0}f <#if followMouse> + (float) Math.atan((screen.getGuiLeft() + ${x + 10} - mouseX) / 40.0)</#if>,
					<#if followMouse>(float) Math.atan((screen.getGuiTop() + ${y + 21 - 50} - mouseY) / 40.0)<#else>0</#if>, livingEntity);
			}
		</#list>
	}
	</#if>

	<#if tooltips?size != 0>
	private static void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		<#list tooltips as component>
			<#assign x = component.gx(getWidth(tooltiptypes, component?index))>
			<#assign y = component.gy(getHeight(tooltiptypes, component?index))>
			<#if tooltiptypes[component?index] == 1>
				if (screen instanceof CreativeModeInventoryScreen inventory && inventory.isInventoryOpen())
			<#else>
				if (<#if hasCurios>(</#if>screen instanceof InventoryScreen<#if hasCurios>) || isCuriosScreen()</#if>)
			</#if>
			<#if hasProcedure(component.displayCondition)>
				if (<@valueProvider component.displayCondition/>)
			</#if>
				if (mouseX > screen.getGuiLeft() + ${x} && mouseX < screen.getGuiLeft() + ${x + component.width} && mouseY > screen.getGuiTop() + ${y} && mouseY < screen.getGuiTop() + ${y + component.height}) {
					<#if hasProcedure(component.text)>
					String hoverText = <@valueProvider component.text/>;
					if (hoverText != null) {
						guiGraphics.renderComponentTooltip(mc.font, Arrays.stream(hoverText.split("\n")).map(Component::literal).collect(Collectors.toList()), mouseX - screen.getGuiLeft(), mouseY - screen.getGuiTop());
					}
					<#else>
						guiGraphics.renderTooltip(mc.font, Component.translatable("invedit.${modid}.${component.getName()}"), mouseX - screen.getGuiLeft(), mouseY - screen.getGuiTop());
					</#if>
				}
		</#list>
	}
	</#if>

	<#if entitymodels?size != 0>
	private static void renderEntityInInventoryFollowsAngle(GuiGraphics guiGraphics, int x, int y, int scale, float angleXComponent, float angleYComponent, LivingEntity entity) {
		Quaternionf pose = new Quaternionf().rotateZ((float)Math.PI);
		Quaternionf cameraOrientation = new Quaternionf().rotateX(angleYComponent * 20 * ((float) Math.PI / 180F));
		pose.mul(cameraOrientation);
		float f2 = entity.yBodyRot;
		float f3 = entity.getYRot();
		float f4 = entity.getXRot();
		float f5 = entity.yHeadRotO;
		float f6 = entity.yHeadRot;
		entity.yBodyRot = 180.0F + angleXComponent * 20.0F;
		entity.setYRot(180.0F + angleXComponent * 40.0F);
		entity.setXRot(-angleYComponent * 20.0F);
		entity.yHeadRot = entity.getYRot();
		entity.yHeadRotO = entity.getYRot();
		InventoryScreen.renderEntityInInventory(guiGraphics, x, y, scale, new Vector3f(0, 0, 0), pose, cameraOrientation, entity);
		entity.yBodyRot = f2;
		entity.setYRot(f3);
		entity.setXRot(f4);
		entity.yHeadRotO = f5;
		entity.yHeadRot = f6;
	}
	</#if>

	private static boolean getFlag() {
		if (screen instanceof InventoryScreen inventory)
			return true;
		if (screen instanceof CreativeModeInventoryScreen inventory)
			return inventory.isInventoryOpen();
		<#if hasCurios>
			if (isCuriosScreen())
				return true;
		</#if>
		return false;
	}

	<#if hasCurios>
		private static boolean isCuriosScreen() {
			return (screen instanceof EffectRenderingInventoryScreen) && !(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen) && screen.getClass().getSimpleName().equals("CuriosScreen");
		}
	</#if>
}

<#macro valueProvider procedure="">
	<@procedureCode procedure, {
		"x": "mc.player.getX()",
		"y": "mc.player.getY()",
		"z": "mc.player.getZ()",
		"world": "mc.level",
		"entity": "mc.player"
	}, false/>
</#macro>

<#macro getSpriteByIndex component dim>
	<#if hasProcedure(component.spriteIndex)>
		Mth.clamp((int) <@valueProvider component.spriteIndex/> *
			<#if dim == "width">
				${component.getWidth(w.getWorkspace())}
			<#else>
				${component.getHeight(w.getWorkspace())}
			</#if>,
			0,
			<#if dim == "width">
				${component.getTextureWidth(w.getWorkspace()) - component.getWidth(w.getWorkspace())}
			<#else>
				${component.getTextureHeight(w.getWorkspace()) - component.getHeight(w.getWorkspace())}
			</#if>
		)
	<#else>
		<#if dim == "width">
			${component.getWidth(w.getWorkspace()) * component.spriteIndex.getFixedValue()}
		<#else>
			${component.getHeight(w.getWorkspace()) * component.spriteIndex.getFixedValue()}
		</#if>
	</#if>
</#macro>

<#macro buttonOnClick component>
e -> {
	<#if hasProcedure(component.onClick)>
	if (getFlag()<#if hasProcedure(component.displayCondition)> && <@valueProvider component.displayCondition/></#if>) {
		PacketDistributor.sendToServer(new ${JavaModName}InventoryButtonMessage(${btid}, (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ()));
		${JavaModName}InventoryButtonMessage.handleButtonAction(mc.player, ${btid}, (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
	}
	</#if>
}
</#macro>

<#function getWidth types index>
	<#return (types[index] == 0)?then(176, 195)>
</#function>

<#function getHeight types index>
	<#return (types[index] == 0)?then(166, 136)>
</#function>