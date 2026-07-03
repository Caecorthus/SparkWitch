package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.GrandWitchFearClientHooks;
import dev.caecorthus.sparkwitch.client.WitchInstinctSuppressionClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Wathe instinct UI present but denies suppressed instinct rendering during Grand Witch spells.
 * 保留 wathe 本能 UI，但在大魔女法术期间拒绝被压制的本能渲染。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 1500)
public abstract class WatheClientFearInstinctMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$suppressInstinctHighlightDuringAreaSpell(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (WitchInstinctSuppressionClientHooks.shouldSuppressSwallowedInstinctHighlight(target)) {
            cir.setReturnValue(-1);
            return;
        }
        if (WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()) {
            cir.setReturnValue(-1);
        }
    }

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
