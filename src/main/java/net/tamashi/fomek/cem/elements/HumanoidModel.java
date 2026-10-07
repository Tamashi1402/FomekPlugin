package net.tamashi.fomek.cem.elements;

import net.mcreator.element.GeneratableElement;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.resources.Model;

import javax.annotation.Nullable;

public class HumanoidModel extends GeneratableElement {
    public String modelName;
    public String helmetModelPart;
    public String bodyModelPart;
    public String armsModelPartL;
    public String armsModelPartR;
    public String leggingsModelPartL;
    public String leggingsModelPartR;

    @Nullable
    public Model getHumanoidModel() {
        return Model.getModelByParams(getModElement().getWorkspace(), modelName, Model.Type.JAVA);
    }

    public HumanoidModel(ModElement element) {
        super(element);
    }
}
