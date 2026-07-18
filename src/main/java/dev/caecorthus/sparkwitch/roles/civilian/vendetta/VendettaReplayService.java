package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the dedicated terminal replay and suppression of caller-owned duplicate action records. */
public final class VendettaReplayService {
    public static final Identifier TERMINAL_REPLAY_EVENT = SparkWitch.id("vendetta_terminal");

    private static final Map<TerminalActionKey, Long> TERMINAL_ACTION_SUPPRESSION = new HashMap<>();
    private static boolean registered;

    private VendettaReplayService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerGlobalEventFormatter(
                TERMINAL_REPLAY_EVENT,
                VendettaReplayService::formatTerminalReplay
        );
    }

    public static void recordTerminal(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        TERMINAL_ACTION_SUPPRESSION.put(
                new TerminalActionKey(killer.getUuid(), victim.getUuid()),
                victim.getServerWorld().getTime() + 1L
        );
        NbtCompound data = new NbtCompound();
        data.putUuid("actor", killer.getUuid());
        data.putUuid("target", victim.getUuid());
        data.putString("death_reason", deathReason.toString());
        GameRecordManager.recordGlobalEvent(
                victim.getServerWorld(), TERMINAL_REPLAY_EVENT, killer, data);
    }

    public static boolean shouldSuppressItemRecord(
            @Nullable ServerPlayerEntity actor,
            @Nullable ServerPlayerEntity target
    ) {
        if (actor == null || target == null) {
            return false;
        }
        if (VendettaInteractionService.isBoundKillerTargetingVendetta(actor, target)) {
            return true;
        }
        return shouldSuppressTerminalRecord(actor, target);
    }

    public static boolean shouldSuppressSkillRecord(
            @Nullable ServerPlayerEntity actor,
            @Nullable ServerPlayerEntity target
    ) {
        if (actor == null || target == null) {
            return false;
        }
        return shouldSuppressTerminalRecord(actor, target);
    }

    private static boolean shouldSuppressTerminalRecord(
            ServerPlayerEntity actor,
            ServerPlayerEntity target
    ) {
        long now = actor.getServerWorld().getTime();
        TERMINAL_ACTION_SUPPRESSION.entrySet().removeIf(entry -> entry.getValue() < now);
        Long suppressUntil = TERMINAL_ACTION_SUPPRESSION.get(
                new TerminalActionKey(actor.getUuid(), target.getUuid()));
        return suppressUntil != null && now <= suppressUntil;
    }

    public static void clearRoundState() {
        TERMINAL_ACTION_SUPPRESSION.clear();
    }

    private static @Nullable Text formatTerminalReplay(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                "replay.global.sparkwitch.vendetta_terminal",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }

    private record TerminalActionKey(UUID actorUuid, UUID targetUuid) {
    }
}
