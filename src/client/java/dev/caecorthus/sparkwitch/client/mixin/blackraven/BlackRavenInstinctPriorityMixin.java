package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HEAD answer for the Black Raven held-instinct x-ray on perceived players. SparkTraits asks the same resolver through its
 * reflective bridge before its Conscience branch, so Conscience cannot recolour or hide the x-ray.
 * 黑羽鸦按住本能键时对已感知玩家透视的 HEAD 结果。SparkTraits 在善良分支之前通过反射桥调用同一裁决器，因此善良不会改色或隐藏透视。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 2000)
public abstract class BlackRavenInstinctPriorityMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$prioritizeBlackRavenXray(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        Integer color = BlackRavenInstinctClientHooks.resolvePrioritySensedHighlight(target);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
