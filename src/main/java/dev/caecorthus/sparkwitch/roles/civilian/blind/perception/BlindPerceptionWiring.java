package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server wiring of the Blind's sound and voice perception. The {@code ServerWorld} hooks
 * ({@code mixin/blind/ServerWorldBlindPerceptionMixin}) feed world sounds and explosions; the existing Simple Voice
 * Chat plugin feeds voice through {@link BlindVoiceInbox}. One end-of-tick pass refreshes the listening Blinds and
 * drains voice; role, death, reset and disconnect events mark the list dirty; round end and server stop clear it.
 * 盲人声音与语音感知的服务端接线。{@code ServerWorld} 钩子（{@code mixin/blind/ServerWorldBlindPerceptionMixin}）提供世界声音
 * 与爆炸；现有的 Simple Voice Chat 插件经 {@link BlindVoiceInbox} 提供语音。每刻末一次处理刷新聆听名单并取出语音；
 * 职业、死亡、重置与断线事件将名单标脏；回合结束与服务器停止时清空。
 */
public final class BlindPerceptionWiring {
    private static boolean registered;

    private BlindPerceptionWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(BlindPerceptionWiring::endServerTick);
        RoleAssigned.EVENT.register((player, role) -> BlindPerceptionTargets.markDirty());
        KillPlayer.AFTER.register((victim, killer, deathReason) -> BlindPerceptionTargets.markDirty());
        ResetPlayer.EVENT.register(player -> BlindPerceptionTargets.markDirty());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlindPerceptionTargets.markDirty());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    private static void endServerTick(MinecraftServer server) {
        BlindPerceptionTargets.tick(server);
        BlindVoiceInbox inbox = BlindVoiceInbox.get();
        if (inbox.size() == 0) {
            return;
        }
        if (!BlindPerceptionTargets.anyActive()) {
            inbox.clear();
            return;
        }
        long now = server.getTicks();
        inbox.drain(now, (speaker, whispering) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(speaker);
            if (player != null) {
                BlindSoundPerception.onVoice(player, whispering);
            }
        });
        if (now % BlindPerceptionTargets.SWEEP_INTERVAL_TICKS == 0) {
            inbox.prune(now);
        }
    }

    private static void clear() {
        BlindPerceptionTargets.clear();
        BlindVoiceInbox.get().clear();
    }
}
