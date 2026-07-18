package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the Vendetta's world label from the bound killer. / 向绑定凶手隐藏仇杀客的世界名称标签。 */
@Mixin(LivingEntityRenderer.class)
public abstract class VendettaPlayerLabelMixin {
    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideVendettaLabel(
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof AbstractClientPlayerEntity target
                && VendettaClientPresentation.isBoundKillerViewingVendetta(
                        MinecraftClient.getInstance().player,
                        target
                )) {
            cir.setReturnValue(false);
        }
    }
}
