package ${package}.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;

@Mixin(Minecraft.class)
public interface MinecraftAccessorMixin {
    @Accessor("timer")
    DeltaTracker.Timer fomek$getTimer();
}
