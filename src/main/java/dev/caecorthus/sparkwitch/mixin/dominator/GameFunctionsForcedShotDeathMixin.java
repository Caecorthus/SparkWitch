package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after Wathe marks the victim dead (the death is committed: every cancel path has returned before it) and well
 * before its death-drop loop, a running forced-shot sequence is settled: the marked gun is taken and the stashed
 * stack goes back, so Wathe's own {@code shouldDropOnDeath} loop drops or keeps it exactly as without the sequence. A
 * SparkTraits-intercepted death (Last Stand) is skipped and the sequence continues. Covers every death path,
 * Wathe's disconnect kill included.
 * Wathe 把受害者标记为死亡之后（死亡已确定：所有取消路径都在此前返回）、其死亡掉落循环之前，结算进行中的强制开枪序列：收走
 * 标记左轮并放回原物品，使 Wathe 自身的 {@code shouldDropOnDeath} 循环与没有该序列时完全一样地掉落或保留它。SparkTraits 拦截
 * 的死亡（背水一战）被跳过，序列继续。覆盖所有死亡路径，含 Wathe 的断线击杀。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsForcedShotDeathMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;markPlayerDead(Ljava/util/UUID;)V",
                    shift = At.Shift.AFTER)
    )
    private static void sparkwitch$settleForcedShotBeforeDeathDrops(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        DominatorForcedShotService.beforeDeathDrops(victim);
    }
}
