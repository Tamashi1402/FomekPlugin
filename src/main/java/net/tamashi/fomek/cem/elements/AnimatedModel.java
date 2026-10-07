package net.tamashi.fomek.cem.elements;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.types.LivingEntity;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ModElementReference;
import net.mcreator.workspace.references.ResourceReference;
import net.mcreator.workspace.resources.Model;

import javax.annotation.Nullable;
import java.util.List;

public class AnimatedModel extends GeneratableElement {
    public String modelName;
    @ModElementReference @ResourceReference("animation") public List<LivingEntity.AnimationEntry> animations;

    @Nullable
    public Model getAnimatedModel() {
        return Model.getModelByParams(getModElement().getWorkspace(), modelName, Model.Type.JAVA);
    }

    public AnimatedModel(ModElement element) {
        super(element);
    }
}
