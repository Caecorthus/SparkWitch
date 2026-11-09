package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rys-T ricochet (BEAR D2) at HEAD of Wathe's 5-arg {@code killPlayer} (Wathe member, {@code remap = false}); a forced
 * kill never ricochets. HEAD callbacks run in ascending mixin priority, so 1050 lands after every default-1000 HEAD
 * guard (SparkFactionAPI's affect veto, Saint, Vendetta, the forced-shot guard, Voodoo, SparkStrength's Jester and
 * Undercover guards: a vetoed attack never rolls or dazes) and before the Fiend and SparkStrength's vest (1100), every
 * {@code KillPlayer.BEFORE} listener, psycho armour, Tofana and the Cat, so a deflected hit spends nothing.
 * SparkTraits' and the Judge's {@code @WrapMethod}s stay outermost; their re-entries reuse the memoised roll.
 * Rys-T 跳弹（BEAR D2），挂在 Wathe 五参 {@code killPlayer} 的 HEAD（Wathe 成员，{@code remap = false}）；强制击杀永不跳弹。
 * HEAD 回调按 mixin 优先级升序执行，因此 1050 位于所有默认 1000 的 HEAD 守卫之后（SparkFactionAPI 影响否决、圣徒、仇杀、
 * 强制开枪守卫、巫毒、SparkStrength 的小丑与卧底守卫：被否决的攻击既不掷骰也不眩晕），并位于魔人与 SparkStrength 护甲（1100）、
 * 所有 {@code KillPlayer.BEFORE} 监听、疯魔护甲、托法娜与猫之前，因此被弹开的命中不消耗任何东西。SparkTraits 与法官的
 * {@code @WrapMethod} 仍在最外层；它们的重入沿用已记忆的掷骰结果。
 */
@Mixin(value = GameFunctions.class, remap = false, priority = 1050)
public abstract class RicochetKillPlayerMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$rysTRicochet(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (BearRicochetService.interceptKill(victim, killer, deathReason, force)) {
            ci.cancel();
        }
    }
}
