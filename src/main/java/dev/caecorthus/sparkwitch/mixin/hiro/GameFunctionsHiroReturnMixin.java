package dev.caecorthus.sparkwitch.mixin.hiro;

import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnService;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroVictimFeignService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 二阶堂希罗's 死亡回归: three seams in Wathe's 5-arg {@code killPlayer}, the only overload that sees {@code force} and
 * {@code spawnBody}. Ordering is the contract (injectors apply by {@code order} first, then by mixin priority, and
 * callbacks at one instruction run in that order):
 * <ul>
 *     <li>HEAD, {@code order = 0}: a fake-dead victim is untouchable, so a second kill is refused before any other
 *     HEAD hook can spend something on it (forced kills too; they ignore a {@code KillPlayer.BEFORE} cancel). Only
 *     the feign service's own replay of the real death passes.</li>
 *     <li>HEAD, {@code order = 900}: a kill attempt on Hiro. It runs before every default-order HEAD guard whatever
 *     its mixin priority (SparkFactionAPI affect veto, Saint, Knight duel, Voodoo, Ceremonial Sword at 1000, Specter
 *     1040, BEAR ricochet 1050, Fiend and the SparkStrength vest 1100, Charon 1200), every BEFORE listener, psycho
 *     armour and Tofana, so a cancelled attack consumes none of her shields (owner rule 6). It only observes unless
 *     the Return fires. The Rope's non-cancelling strangle-interrupt observer (default order, priority 1030) runs
 *     after it, and not at all when the Return cancels the kill: {@code HiroReturnService} then interrupts her
 *     strangle itself. SparkTraits' and the Judge's {@code @WrapMethod}s stay outermost: a kill they refuse (Last
 *     Escape immunity, a sentenced attacker) never reaches this hook and is no attack.</li>
 *     <li>On the alive branch's {@code changeGameMode(SPECTATOR)} call itself (no shift), {@code order = 1200}: after
 *     SparkWitch Tofana (one instruction earlier), SparkTraits Last Escape (default order) and the Schrödinger's Cat
 *     takeover (order 1100) on the same call, and after every {@code @WrapMethod} pre-call, HEAD veto, BEFORE listener
 *     and psycho armour — owner rule 2: only a death nothing else stopped becomes a fake death. Cancelling there
 *     skips the death mark, kill reward, corpse, drops and {@code KillPlayer.AFTER}, like Tofana and the Cat; they
 *     happen in the replay when the victim's timer ends.</li>
 * </ul>
 * Every other victim returns at once from all three. Server only.
 * 二阶堂希罗的「死亡回归」：Wathe 五参 {@code killPlayer}（唯一能看到 {@code force} 与 {@code spawnBody} 的重载）中的三个
 * 接缝，顺序即契约（注入器先按 {@code order}、再按 mixin 优先级应用，同一指令处的回调按此顺序执行）。HEAD
 * （{@code order = 0}）：假死的受害者无法被触及，在其他 HEAD 钩子为其消耗任何东西之前就拒绝第二次击杀（强制击杀同样拒绝，
 * 它们无视 {@code KillPlayer.BEFORE} 的取消）；只有假死服务自身对真正死亡的重放放行。HEAD（{@code order = 900}）：针对希罗
 * 的击杀尝试，先于所有默认 order 的 HEAD 守卫（不论其 mixin 优先级：默认 1000 的 SparkFactionAPI 影响否决、圣徒、骑士决斗、
 * 巫毒、仪礼剑，幽魂 1040，BEAR 跳弹 1050，魔人与 SparkStrength 防弹衣 1100，卡戎 1200）、所有 BEFORE 监听器、疯魔护甲与
 * 托法娜，因此被取消的攻击不会消耗她的任何护盾（所有者规则 6）；除非触发回归，否则只做观察。绳索不取消击杀的勒杀打断观察器
 * （默认 order、优先级 1030）排在它之后，回归取消击杀时则完全不会执行：此时由 {@code HiroReturnService} 自行打断她的勒杀。
 * SparkTraits 与法官的
 * {@code @WrapMethod} 仍在最外层：被它们拒绝的击杀（脱险无敌、被判决的攻击者）不会到达此钩子，也不算攻击。存活分支的
 * {@code changeGameMode(SPECTATOR)} 调用本身（不偏移，{@code order = 1200}）：排在同一调用上的 SparkWitch 托法娜（前一条
 * 指令）、SparkTraits 绝境逃生（默认 order）与薛定谔之猫接管（order 1100）之后，也在所有 {@code @WrapMethod} 调用前逻辑、
 * HEAD 否决、BEFORE 监听器与疯魔护甲之后——所有者规则 2：只有没被任何机制挡下的死亡才变成假死。在此取消会跳过死亡标记、
 * 击杀奖励、尸体、掉落与 {@code KillPlayer.AFTER}，与托法娜和猫一致；它们在受害者计时结束后的重放中发生。其他受害者在三处
 * 都立即返回。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsHiroReturnMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1,
            order = 0
    )
    private static void sparkwitch$feignedVictimIsUntouchable(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (HiroVictimFeignService.blocksKill(victim)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1,
            order = 900
    )
    private static void sparkwitch$returnOnAttack(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (HiroReturnService.onKillAttempt(victim, killer, deathReason, force)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;changeGameMode(Lnet/minecraft/world/GameMode;)Z"
            ),
            cancellable = true,
            require = 1,
            allow = 1,
            order = 1200
    )
    private static void sparkwitch$feignAtDeathCommit(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (HiroVictimFeignService.onDeathCommit(victim, spawnBody, killer, deathReason)) {
            ci.cancel();
        }
    }
}
