package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/** Outer guard includes force and terminal HEAD paths. / 外层守卫覆盖强制致死及终结 HEAD。 */
@Mixin(value = GameFunctions.class, priority = 500)
public abstract class JudgeKillCompletionMixin {
    // MixinExtras 0.5 appends wrappers around the previous stage: lower priority is offered later/outer.
    // 0.5 版包装器包住前一层；较低优先级后应用，位于 Traits 2000 与 HEAD 1000 的外侧。
    @WrapMethod(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V")
    private static void sparkwitch$judgeGuard(ServerPlayerEntity victim, boolean spawnBody,
                                            ServerPlayerEntity killer, Identifier reason, boolean force,
                                            Operation<Void> original) {
        JudgeKillAttribution.attempt(victim, killer,
                () -> original.call(victim, spawnBody, killer, reason, force));
    }

    @WrapOperation(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;markPlayerDead(Ljava/util/UUID;)V"))
    private static void sparkwitch$judgeRecordCommit(GameWorldComponent game, UUID victimId,
                                                     Operation<Void> original,
                                                     ServerPlayerEntity victim, boolean spawnBody,
                                                     ServerPlayerEntity killer, Identifier reason, boolean force) {
        boolean wasDead = game.isPlayerDead(victimId);
        original.call(game, victimId);
        if (!wasDead && game.isPlayerDead(victimId)) JudgeKillAttribution.committed(victim);
    }
}
