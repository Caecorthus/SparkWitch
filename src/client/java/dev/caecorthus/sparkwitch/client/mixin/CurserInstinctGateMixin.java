package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.curser.CurserClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents only the confused local player from activating instinct. / 仅阻止受混乱影响的本地玩家激活本能。 */
@Mixin(value = WatheClient.class, remap = false, priority = 1600)
public abstract class CurserInstinctGateMixin {
    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$blockConfusedInstinct(CallbackInfoReturnable<Boolean> cir) {
        if (CurserClientHooks.isLocallyConfused()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInstinctEnabledAndIsKiller", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$blockConfusedKillerInstinct(CallbackInfoReturnable<Boolean> cir) {
        if (CurserClientHooks.isLocallyConfused()) {
            cir.setReturnValue(false);
        }
    }
}
