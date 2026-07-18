package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Owns Wraith task progress and the promotion it unlocks.
 * 统一负责冤魂任务进度及其解锁的晋升流程。
 */
public final class WraithProgression {
    private static boolean registered;

    private WraithProgression() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register(WraithProgression::onTaskComplete);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> WraithTaskRuntime.onDisconnect(handler.player)
        );
        ServerTickEvents.END_SERVER_TICK.register(WraithPromotionQueue::finishPromotions);
    }

    public static WraithTaskSnapshot capture(PlayerEntity player) {
        return WraithTaskSnapshot.capture(player);
    }

    public static void restoreForActivation(ServerPlayerEntity player, WraithTaskSnapshot snapshot) {
        WraithTaskRuntime.restoreForActivation(player, snapshot);
    }

    public static void resumePlayer(ServerPlayerEntity player) {
        WraithTaskRuntime.resumePlayer(player);
        WraithPromotionQueue.resumePlayer(player);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        WraithPromotionQueue.clearPlayer(player);
        WraithTaskRuntime.clearPlayer(player);
    }

    public static void clearAll() {
        WraithPromotionQueue.clearAll();
        WraithTaskRuntime.clearAll();
    }

    /** Runs Wathe's task loop without running any mood behavior. */
    public static void tick(PlayerMoodComponent component, PlayerEntity player) {
        WraithTaskRuntime.tick(component, player);
    }

    /**
     * Preserves NoellesRoles' native-killer exclusion except for the promoted Saboteur identity.
     * 保留 NoellesRoles 的原生杀手过滤，仅对晋升后的破坏者放行。
     */
    public static boolean shouldExcludeFromAssassinGuess(Role role) {
        return WraithPromotionRoles.shouldExcludeFromAssassinGuess(role);
    }

    private static void onTaskComplete(ServerPlayerEntity player, PlayerMoodComponent.Task taskType) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        if (!wraith.isActive()) {
            return;
        }
        int completions = wraith.recordTaskCompletion();
        WraithPromotionQueue.queueIfReady(player, completions);
    }
}
