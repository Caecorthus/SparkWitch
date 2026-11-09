package dev.caecorthus.sparkwitch.mixin.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPardonService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Charon's pardon window and gaze feign (Wathe member, {@code remap = false}) as a cancellable HEAD guard on the
 * 5-arg {@code killPlayer}, the only overload that sees {@code force} and {@code spawnBody}: a forced kill is never
 * held, and the replay keeps the original {@code spawnBody}. Order: HEAD callbacks run in ascending mixin priority,
 * so 1200 runs after every default-1000 guard (SparkFactionAPI affect veto, Saint, Vendetta, Wraith capture, Knight
 * duel, Witch Maiden Voodoo, Ceremonial Sword), the Specter (1040), BEAR's ricochet (1050) and the Fiend /
 * SparkStrength vest (1100) — those still spend first, exactly as when the pardon was a {@code KillPlayer.BEFORE}
 * listener — and before every BEFORE listener, psycho armour, Tofana and the Schrödinger's Cat takeover. Returning
 * without a cancel leaves the BEFORE chain untouched. SparkTraits' and the Judge's {@code @WrapMethod}s stay outermost.
 * 卡戎的宽恕窗口与「瞥视」假死（Wathe 成员，{@code remap = false}）：五参 {@code killPlayer} 上可取消的 HEAD 守卫，
 * 这是唯一能看到 {@code force} 与 {@code spawnBody} 的重载：强制击杀从不扣住，重放保留原 {@code spawnBody}。
 * 顺序：HEAD 回调按 mixin 优先级升序执行，因此 1200 排在所有默认 1000 的守卫（SparkFactionAPI 影响否决、圣徒、仇杀、
 * 冤魂捕获、骑士决斗、巫女巫毒、仪礼剑）、幽魂（1040）、BEAR 跳弹（1050）以及魔人 / SparkStrength 防弹衣（1100）之后——
 * 它们仍先结算，与宽恕曾是 {@code KillPlayer.BEFORE} 监听器时一致——并先于所有 BEFORE 监听器、疯魔护甲、托法娜与
 * 薛定谔之猫的接管。不取消时 BEFORE 链完全不受影响。SparkTraits 与法官的 {@code @WrapMethod} 仍在最外层。
 */
@Mixin(value = GameFunctions.class, remap = false, priority = 1200)
public abstract class GameFunctionsFerrymanPardonMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$holdCharonKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (FerrymanPardonService.interceptKill(victim, spawnBody, killer, deathReason, force)) {
            ci.cancel();
        }
    }
}
