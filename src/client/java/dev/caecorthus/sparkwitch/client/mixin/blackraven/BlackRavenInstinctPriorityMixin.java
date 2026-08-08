package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prioritizes Black Raven sensed snapshots before SparkTraits Conscience can cancel Wathe's highlight. / 在 SparkTraits 善良取消 Wathe 高亮前，优先处理黑羽鸦感知快照。 */
@Mixin(value = WatheClient.class, remap = false, priority = 2000)
public abstract class BlackRavenInstinctPriorityMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$prioritizeBlackRavenSensedInstinct(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        Integer color = BlackRavenInstinctClientHooks.resolvePrioritySensedHighlight(target);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
