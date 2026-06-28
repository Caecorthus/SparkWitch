package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.GrandWitchFearClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Wathe instinct UI present but denies instinct use during Fear.
 * 保留 wathe 本能 UI，只在恐惧期间拒绝实际本能使用。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientFearInstinctMixin {
    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableInstinctWhenFeared(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockInstinct()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInstinctEnabledAndIsKiller", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableKillerInstinctWhenFeared(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockInstinct()) {
            cir.setReturnValue(false);
        }
    }
}
