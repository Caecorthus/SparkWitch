package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Fiend Moment lifecycle, server only. Ends the moment on the moment Fiend's final death (a SparkTraits Last Stand
 * interception keeps the Fiend in play, and the win listener catches any later death; a disconnect,
 * {@code wathe:escaped}, ends it as "ended", not "slain") or on a role change away from the Fiend; clears it and the
 * spent-Fiend ledger silently at round start (player reset outside ACTIVE, then finish-initialize), at finalize and at
 * server stop, so neither ever leaks across rounds (the ledger is also bound to the match id). A Taotie swallow is
 * detected by the win listener. Every other player, role and event is untouched.
 * 魔人时刻生命周期，仅服务端。时刻中的魔人最终死亡（SparkTraits 背水一战的拦截会让魔人留在对局中，之后的死亡由胜负监听器兜底；
 * 断线 {@code wathe:escaped} 以「已结束」而非「已被击杀」结束）或职业变为非魔人时结束时刻；在开局（非 ACTIVE 时的玩家重置、
 * 随后的初始化完成）、结算与服务器停止时静默清除时刻与「已耗尽魔人」登记表，因此二者绝不会跨回合残留（登记表同时绑定
 * 对局 id）。饕餮吞噬由胜负监听器检测。其他玩家、职业与事件均不受影响。
 */
final class FiendLifecycleService {
    private static boolean registered;

    private FiendLifecycleService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(FiendLifecycleService::afterKill);
        RoleAssigned.EVENT.register(FiendLifecycleService::onRoleAssigned);
        ResetPlayer.EVENT.register(FiendLifecycleService::onReset);
        GameEvents.ON_FINISH_INITIALIZE.register(FiendLifecycleService::clearWorld);
        GameEvents.ON_FINISH_FINALIZE.register(FiendLifecycleService::clearWorld);
        ServerLifecycleEvents.SERVER_STOPPED.register(FiendLifecycleService::onServerStopped);
    }

    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer, Identifier deathReason) {
        if (victim == null) {
            return;
        }
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(victim.getServerWorld());
        boolean momentFiend = moment.isMomentFiend(victim.getUuid());
        if (!momentFiend) {
            return;
        }
        if (FiendMomentRules.endsOnDeath(momentFiend, WitchFactorTraitsBridge.isDeathIntercepted(victim))) {
            FiendMomentService.end(victim.getServerWorld(), FiendMomentRules.deathEnd(moment.isComplete(),
                    GameConstants.DeathReasons.ESCAPED.equals(deathReason)));
        }
    }

    private static void onRoleAssigned(PlayerEntity player, Role role) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(serverPlayer.getServerWorld());
        if (FiendMomentRules.endsOnRoleAssigned(moment.isMomentFiend(serverPlayer.getUuid()),
                FiendParticipation.isFiendRole(role))) {
            FiendMomentService.end(serverPlayer.getServerWorld(), FiendMomentRules.EndReason.ENDED);
        }
    }

    private static void onReset(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(world);
        boolean gameActive = GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (FiendMomentRules.clearsOnReset(moment.isActive(), moment.isMomentFiend(player.getUuid()), gameActive)) {
            FiendMomentService.end(world, FiendMomentRules.EndReason.SILENT);
        }
        if (!gameActive) {
            moment.clearSpent();
        }
        FiendMomentEffects.forget(player.getUuid());
    }

    private static void clearWorld(World world, GameWorldComponent game) {
        if (world instanceof ServerWorld serverWorld) {
            FiendMomentService.end(serverWorld, FiendMomentRules.EndReason.SILENT);
            FiendMomentWorldComponent.get(serverWorld).clearSpent();
        }
    }

    private static void onServerStopped(MinecraftServer server) {
        FiendMomentEffects.forgetAll();
    }
}
