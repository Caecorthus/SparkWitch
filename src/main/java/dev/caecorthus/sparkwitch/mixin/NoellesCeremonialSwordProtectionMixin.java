package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.NoellesCeremonialSwordProtectionCompat;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pinned 1.7.6 listener: for the ceremonial sword and the Savior's terminal kills, consume every applicable layer
 * (Iron Man buff, whiskey shield) and skip this listener entirely, then let later BEFORE listeners run. Skipping it
 * also prevents its Jester fake-death branch from starting a moment for a victim the Savior is about to kill.
 * 锁定 1.7.6 的监听：仪礼剑与救世主终结死因先消耗适用保护（铁人 buff、威士忌盾）并完全跳过本监听，再继续后续
 * BEFORE 监听。跳过它也避免其小丑假死分支为即将被杀死的目标开启时刻。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesCeremonialSwordProtectionMixin {
    @Inject(method = "lambda$registerEvents$5", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void sparkwitch$consumeWithoutBlocking(ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer, Identifier deathReason,
            CallbackInfoReturnable<KillPlayer.KillResult> cir) {
        if (!CeremonialSwordProtectionPolicy.pierces(deathReason)
                && !SaviorRules.isTerminalKill(deathReason)) {
            return;
        }
        NoellesCeremonialSwordProtectionCompat.consumeProtections(victim, killer, deathReason);
        // Swallowed no-body behavior is preserved at GameFunctions, without short-circuiting listeners.
        // 吞噬者无尸体规则由 GameFunctions 接缝保留，不短路其他监听。
        cir.setReturnValue(null);
    }
}
