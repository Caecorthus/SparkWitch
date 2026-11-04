package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 薛定谔之猫's two seams in Wathe's {@code killPlayer}. Ordering is the contract:
 * <ul>
 *     <li>HEAD, {@code order = 0}: a cat in its 3 s spectre is untouchable, so the kill is refused before any other
 *     HEAD hook can spend something on it (forced kills too; they skip {@code KillPlayer.BEFORE}).</li>
 *     <li>Right before the alive branch's {@code changeGameMode(SPECTATOR)}, {@code order = 1100}: callbacks at one
 *     call site run in ascending order, so this one runs after every default-order injector there (SparkTraits Last
 *     Escape at priority 900, SparkWitch Tofana at 1000) and after every HEAD veto, BEFORE listener and psycho armour
 *     — owner 2026-11-04: protections are spent first, the cat transforms only on a death that really commits.</li>
 * </ul>
 * Cancelling there skips the death mark, kill reward, corpse, drops and {@code KillPlayer.AFTER}, like Tofana.
 * Every non-cat victim returns at once. Server only.
 * 薛定谔之猫在 Wathe {@code killPlayer} 中的两个接缝，顺序即契约：HEAD（{@code order = 0}）：处于 3 秒幽魂阶段的猫
 * 无法被击杀，在其他 HEAD 钩子消耗任何东西之前就拒绝本次击杀（强制击杀同样拒绝，它们会跳过 {@code KillPlayer.BEFORE}）。
 * 存活分支 {@code changeGameMode(SPECTATOR)} 之前（{@code order = 1100}）：同一调用点的回调按 order 升序执行，因此它在该处
 * 所有默认顺序注入（SparkTraits 绝境逃生 priority 900、SparkWitch 托法娜 1000）之后，也在所有 HEAD 否决、BEFORE 监听器
 * 与狂暴护甲之后执行——所有者 2026-11-04：先消耗保护，只有真正落定的死亡才让猫转化。在此取消会跳过死亡标记、击杀奖励、
 * 尸体、掉落与 {@code KillPlayer.AFTER}，与托法娜一致。非猫受害者立即返回。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsSchrodingersCatMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            order = 0
    )
    private static void sparkwitch$spectreIsUntouchable(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (SchrodingersCatService.isSpectre(victim)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;changeGameMode(Lnet/minecraft/world/GameMode;)Z",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            require = 1,
            allow = 1,
            order = 1100
    )
    private static void sparkwitch$transformAtDeathCommit(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (SchrodingersCatService.onDeathCommit(victim, killer, deathReason)) {
            ci.cancel();
        }
    }
}
