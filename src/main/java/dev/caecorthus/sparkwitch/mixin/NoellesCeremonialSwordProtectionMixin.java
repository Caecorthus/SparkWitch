package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.NoellesCeremonialSwordProtectionCompat;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned 1.7.6 listener: consume every applicable layer, then let later BEFORE listeners run.
 * 锁定 1.7.6 的监听：逐层消耗适用保护，再继续后续 BEFORE 监听。 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesCeremonialSwordProtectionMixin {
    @Inject(method = "lambda$registerEvents$5", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void sparkwitch$consumeWithoutBlocking(ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer, Identifier deathReason,
            CallbackInfoReturnable<KillPlayer.KillResult> cir) {
        if (!CeremonialSwordProtectionPolicy.pierces(deathReason)) {
            return;
        }
        NoellesCeremonialSwordProtectionCompat.consumeProtections(victim, killer, deathReason);
        // Swallowed no-body behavior is preserved at GameFunctions, without short-circuiting listeners.
        // 吞噬者无尸体规则由 GameFunctions 接缝保留，不短路其他监听。
        cir.setReturnValue(null);
    }
}
