package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotScope;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The forced shot never causes 小脑 ({@code wathe:shot_innocent}) for its shooter (owner decision): every such kill of
 * the shooter, forced ones included, is refused at HEAD while the shot or one of its scheduled consequences runs
 * ({@link DominatorForcedShotScope#coversConsequencesOf}). This covers post-kill rules on {@code KillPlayer.AFTER}
 * (SparkTraits Conscience, Recruit/Noa/Knight/Pig God, Veteran-style knife rules) and delayed punishments queued on
 * Wathe's {@code Scheduler}; the receiver's own punishment is cancelled in {@link DominatorForcedShotGunMixin}. A
 * shot, stab or push the marked player makes by hand during the 1 s sequence is outside the scope and judged normally.
 * 强制开枪绝不会让射手“小脑”（{@code wathe:shot_innocent}，所有者决定）：在这一枪或其排定后果运行期间，对射手的每次此类击杀
 * （含强制击杀）都在 HEAD 被拒绝。覆盖 {@code KillPlayer.AFTER} 上的击杀后规则（SparkTraits 良心、新兵/诺亚/骑士/皮革噶的、
 * 老兵式刀规则）与排入 Wathe {@code Scheduler} 的延迟惩罚；接收器自身的惩罚由 {@link DominatorForcedShotGunMixin} 取消。
 * 被标记者在 1 秒序列内亲手开枪、刺击或推人不在作用域内，照常结算。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsForcedShotProtectionMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkwitch$blockForcedShotInnocentDeath(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (GameConstants.DeathReasons.SHOT_INNOCENT.equals(deathReason)
                && DominatorForcedShotScope.coversConsequencesOf(victim)) {
            ci.cancel();
        }
    }
}
