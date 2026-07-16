package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disables Wathe's outline and light gates only during active Perception. / 仅在感知活动期关闭 Wathe 外框与环境增亮入口。 */
@Mixin(value = WatheClient.class, remap = false, priority = 1600)
public abstract class BlackRavenInstinctGateMixin {
    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableInstinctDuringPerception(CallbackInfoReturnable<Boolean> cir) {
        if (BlackRavenClientState.isPerceptionActive(MinecraftClient.getInstance().player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInstinctEnabledAndIsKiller", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableKillerInstinctDuringPerception(CallbackInfoReturnable<Boolean> cir) {
        if (BlackRavenClientState.isPerceptionActive(MinecraftClient.getInstance().player)) {
            cir.setReturnValue(false);
        }
    }
}
