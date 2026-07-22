package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithConversion;
import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithProgression;
import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithTaskSnapshot;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaDisconnectService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaLifecycleService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaReplayService;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Owns Wraith activation, reconnect, world maintenance, role transitions, and terminal cleanup.
 * 负责冤魂激活、重连、世界维护、身份切换与终止清理。
 */
public final class WraithLifecycle {
    private static boolean registered;

    private WraithLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WraithProgression.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerTickEvents.END_WORLD_TICK.register(WraithLifecycle::tickWorld);
        WraithParticipation.register();
        WraithConversion.register();
    }

    /**
     * Completes the role, runtime, and task portion of a confirmed conversion in fixed order.
     * 按固定顺序完成已确认转化的身份、运行态与任务步骤。
     */
    public static void activateConvertedPlayer(ServerPlayerEntity player, WraithTaskSnapshot taskSnapshot) {
        wakeIfSleeping(player);
        transitionRole(player, SparkWitchRoles.wraith());
        if (player.isSpectator()) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
        WraithPresence.apply(player, true);
        restoreLivingVoice(player);
        WraithProgression.restoreForActivation(player, taskSnapshot);
    }

    /**
     * Applies the post-state-change promotion effects without changing component state itself.
     * 在组件状态已经晋升后应用身份与效果变化，本方法不修改组件状态。
     */
    public static void promotePlayer(ServerPlayerEntity player, Role role) {
        wakeIfSleeping(player);
        transitionRole(player, role);
        SaboteurFeatureService.initializePromotion(player);
        if (role == SparkWitchRoles.curser()) {
            CurserFeatureService.initializeForPromotion(player);
        } else {
            CurserPlayerComponent.KEY.get(player).clear();
        }
        WraithPresence.removeRestrictedEffects(player);
        GuardianAngelFeatureService.initializeForPromotion(player, role);
        VendettaLifecycleService.initializeForPromotion(player, role);
        if (!WraithStateService.isActive(player)) {
            return;
        }
        applyPromotedVoiceGroup(player, role);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        boolean wasActive = wraith.isActive();
        if (wasActive) {
            wakeIfSleeping(player);
        }
        WraithConversion.clearPlayer(player);
        WraithProgression.clearPlayer(player);
        SaboteurPlayerComponent.KEY.get(player).clear();
        CurserFeatureService.clearPlayer(player);
        wraith.clear();
        WraithPresence.clear(player);
        GuardianAngelFeatureService.detachPlayer(player);
        VendettaLifecycleService.clearPlayer(player);
        if (wasActive) {
            restoreLivingVoice(player);
        }
    }

    public static void clearRoundState(ServerWorld world) {
        WraithProgression.clearAll();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (WraithStateService.isActive(player)) {
                clearPlayer(player);
                SparkTraitsWraithBridge.clear(player, true);
            }
        }
        WraithConversion.clearRound(world);
        VendettaDisconnectService.clearRoundState();
        VendettaReplayService.clearRoundState();
    }

    static boolean shouldTerminateForFall(boolean active, double playerY, double minimumY) {
        return active && playerY < minimumY;
    }

    static boolean shouldResume(boolean active, boolean running, boolean hasRole, boolean markedDead) {
        return active && running && hasRole && markedDead;
    }

    /** Ends promoted participation without dispatching Wathe's ordinary death pipeline. */
    public static void terminatePromotedPlayer(ServerPlayerEntity player) {
        clearPlayer(player);
        SparkTraitsWraithBridge.clear(player, false);
        PlayerMoodComponent.KEY.get(player).reset();
        player.changeGameMode(GameMode.SPECTATOR);
        TrainVoicePlugin.addPlayer(player.getUuid());
    }

    private static void onJoin(ServerPlayerEntity player) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!wraith.isActive()) {
            return;
        }
        wakeIfSleeping(player);
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        UUID uuid = player.getUuid();
        if (!shouldResume(true, game.isRunning(), game.hasAnyRole(uuid), game.isPlayerDead(uuid))) {
            clearPlayer(player);
            SparkTraitsWraithBridge.clear(player, true);
            if (player.isSpectator()) {
                TrainVoicePlugin.addPlayer(player.getUuid());
            }
            return;
        }
        if (wraith.isRestricted() && !game.isRole(player, SparkWitchRoles.wraith())) {
            transitionRole(player, SparkWitchRoles.wraith());
        }
        if (!VendettaLifecycleService.resumePlayer(player)) {
            return;
        }
        WraithPresence.apply(player, wraith.isRestricted());
        Role role = game.getRole(player);
        GuardianAngelFeatureService.resumePlayer(player);
        applyPromotedVoiceGroup(player, role);
        WraithProgression.resumePlayer(player);
    }

    private static void tickWorld(ServerWorld world) {
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        for (ServerPlayerEntity player : world.getPlayers()) {
            WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
            if (!wraith.isActive()) {
                continue;
            }
            if (playArea != null && shouldTerminateForFall(true, player.getY(), playArea.minY)) {
                // Falling ends Wraith participation, while target-owned effects finish independently.
                // 坠落会终止冤魂参与，但目标持有的效果仍独立结束。
                terminatePromotedPlayer(player);
                continue;
            }
            WraithPresence.apply(player, wraith.isRestricted());
        }
    }

    private static void wakeIfSleeping(ServerPlayerEntity player) {
        if (player.isSleeping()) {
            // Use vanilla wake-up so the bed occupancy and world sleep counters are released together.
            // 使用原版唤醒流程，同时释放床位并更新世界睡眠计数。
            player.wakeUp(true, true);
        }
    }

    private static void transitionRole(ServerPlayerEntity player, Role role) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        game.addRole(player, role);
        game.sync();
        WraithRoleAnnouncementService.announceCurrentRole(player);
    }

    private static void restoreLivingVoice(ServerPlayerEntity player) {
        TrainVoicePlugin.resetPlayer(player.getUuid());
    }

    private static void applyPromotedVoiceGroup(ServerPlayerEntity player, Role role) {
        if (GuardianAngelRules.isGuardianAngel(role)) {
            // Guardian Angel speaks inside Wathe's hidden dead/spectator group instead of proximity voice.
            // 守护天使只在 Wathe 的隐藏死者/旁观者语音组内发言，不进入生者近距离语音。
            TrainVoicePlugin.addPlayer(player.getUuid());
        } else {
            restoreLivingVoice(player);
        }
    }
}
