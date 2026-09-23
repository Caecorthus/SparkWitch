package dev.caecorthus.sparkwitch.item.firepoker;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks short-lived Fire Poker push credit for train-fall deaths.
 * 记录短期烧火棍推人归因，只在坠车死亡结算时补充 killer。
 */
public final class FirePokerFallAttributionService {
    private static final Map<UUID, Attribution> ATTRIBUTIONS = new HashMap<>();
    // Responsibility outlives entity cleanup, but uses the same original push window and one-shot consumption.
    // 责任 UUID 不随实体清理消失，但沿用原推力窗口与单次消费，不改变 killer 奖励归属。
    private static final Map<UUID, Attribution> JUDGE_ATTRIBUTIONS = new HashMap<>();

    private FirePokerFallAttributionService() {
    }

    public static void recordPush(ServerPlayerEntity pusher, ServerPlayerEntity target) {
        recordPush(pusher.getUuid(), target.getUuid(), pusher.getServerWorld().getTime());
    }

    static void recordPush(UUID pusherUuid, UUID targetUuid, long currentTime) {
        if (pusherUuid.equals(targetUuid)) {
            return;
        }
        clearExpired(currentTime);
        Attribution attribution = new Attribution(pusherUuid, currentTime + FirePokerRules.FALL_ATTRIBUTION_WINDOW_TICKS);
        ATTRIBUTIONS.put(targetUuid, attribution);
        JUDGE_ATTRIBUTIONS.put(targetUuid, attribution);
    }

    public static void resolveFallKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity fallbackKiller,
            Identifier deathReason
    ) {
        Attribution saved = JUDGE_ATTRIBUTIONS.remove(victim.getUuid());
        UUID source = saved != null && victim.getServerWorld().getTime() <= saved.expiresAtTicks()
                ? saved.pusherUuid() : null;
        UUID responsible = dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution.resolve(victim, source);
        ServerPlayerEntity resolved = resolveFallKiller(victim, fallbackKiller, deathReason);
        dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution.runWith(
                victim.getServerWorld(), responsible,
                () -> GameFunctions.killPlayer(victim, spawnBody, resolved, deathReason));
    }

    public static @Nullable ServerPlayerEntity resolveFallKiller(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity fallbackKiller,
            Identifier deathReason
    ) {
        UUID pusherUuid = consumePusherUuid(victim.getUuid(), deathReason, victim.getServerWorld().getTime());
        if (pusherUuid == null || victim.getServer() == null) {
            return fallbackKiller;
        }

        ServerPlayerEntity pusher = victim.getServer().getPlayerManager().getPlayer(pusherUuid);
        if (pusher == null) {
            return fallbackKiller;
        }
        return pusher;
    }

    static @Nullable UUID consumePusherUuid(UUID targetUuid, Identifier deathReason, long currentTime) {
        if (!GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            return null;
        }

        Attribution attribution = ATTRIBUTIONS.remove(targetUuid);
        if (attribution == null || currentTime > attribution.expiresAtTicks()) {
            clearExpired(currentTime);
            return null;
        }
        return attribution.pusherUuid();
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        clearPlayer(player.getUuid());
    }

    static void clearPlayer(UUID playerUuid) {
        ATTRIBUTIONS.remove(playerUuid);
        ATTRIBUTIONS.entrySet().removeIf(entry -> entry.getValue().pusherUuid().equals(playerUuid));
        JUDGE_ATTRIBUTIONS.remove(playerUuid);
    }

    public static void clearAll() {
        ATTRIBUTIONS.clear();
        JUDGE_ATTRIBUTIONS.clear();
    }

    private static void clearExpired(long currentTime) {
        JUDGE_ATTRIBUTIONS.entrySet().removeIf(entry -> currentTime > entry.getValue().expiresAtTicks());
        Iterator<Map.Entry<UUID, Attribution>> iterator = ATTRIBUTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Attribution> entry = iterator.next();
            if (currentTime > entry.getValue().expiresAtTicks()) {
                iterator.remove();
            }
        }
    }

    private record Attribution(UUID pusherUuid, long expiresAtTicks) {
    }
}
