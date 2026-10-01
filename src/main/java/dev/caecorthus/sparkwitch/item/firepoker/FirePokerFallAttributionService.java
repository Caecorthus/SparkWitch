package dev.caecorthus.sparkwitch.item.firepoker;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived push credit for train-fall deaths, shared by every pushing weapon (owner decision D3, generalised from
 * the Fire Poker for the Abyss Listener Shriek Gun; the class keeps its historical name). Each record stores the
 * pusher, the pushing weapon and the window end; a {@code wathe:fell_out_of_train} death inside the window is credited
 * to the latest pusher and runs with that weapon as the SparkTraits non-final kill weapon. Pushes by different weapons
 * share one ledger, so the latest push wins regardless of weapon. Server-only.
 * 坠车死亡的短期推人归因，所有推人武器共用（所有者决定 D3：为聆渊者啸音铳从烧火棍通用化而来，类名保持历史名称）。
 * 每条记录保存推人者、推人所用武器与窗口结束时间；窗口内的 {@code wathe:fell_out_of_train} 死亡记给最近一次推人者，
 * 并以该武器作为 SparkTraits 非最终击杀武器执行。不同武器的推人共用一本账，因此无论武器如何，都以最近一次推人为准。仅服务端。
 */
public final class FirePokerFallAttributionService {
    private static final Ledger<Item> LEDGER = new Ledger<>();

    private FirePokerFallAttributionService() {
    }

    /**
     * Records that {@code pusher} just pushed {@code target} with {@code weapon}; a self-push is ignored.
     * 记录 {@code pusher} 刚用 {@code weapon} 推了 {@code target}；推自己时忽略。
     */
    public static void recordPush(ServerPlayerEntity pusher, ServerPlayerEntity target, Item weapon) {
        if (pusher == null || target == null || weapon == null) {
            return;
        }
        LEDGER.record(pusher.getUuid(), target.getUuid(), weapon, pusher.getServerWorld().getTime());
    }

    public static void resolveFallKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity fallbackKiller,
            Identifier deathReason
    ) {
        long now = victim.getServerWorld().getTime();
        UUID source = LEDGER.takeResponsibleSource(victim.getUuid(), now);
        UUID responsible = dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution.resolve(victim, source);
        Attribution<Item> credit = consumeCredit(victim, deathReason, now);
        ServerPlayerEntity pusher = credit == null ? null : pusherOf(victim, credit);
        ServerPlayerEntity resolved = pusher != null ? pusher : fallbackKiller;
        Runnable kill = () -> GameFunctions.killPlayer(victim, spawnBody, resolved, deathReason);
        // Only a credited push makes its recorded weapon the weapon; Traits discounts it if the fall turns non-final.
        // 只有被记为推人者的那次推击，其记录的武器才算凶器；若坠车成为非最终击杀，由 Traits 缩短其冷却。
        dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution.runWith(
                victim.getServerWorld(), responsible,
                pusher == null ? kill
                        : () -> SparkTraitsKillerBridge.runWithNonFinalKillWeapon(credit.weapon(), kill));
    }

    public static @Nullable ServerPlayerEntity resolveFallKiller(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity fallbackKiller,
            Identifier deathReason
    ) {
        Attribution<Item> credit = consumeCredit(victim, deathReason, victim.getServerWorld().getTime());
        ServerPlayerEntity pusher = credit == null ? null : pusherOf(victim, credit);
        return pusher != null ? pusher : fallbackKiller;
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        LEDGER.clearPlayer(player.getUuid());
    }

    public static void clearAll() {
        LEDGER.clearAll();
    }

    private static @Nullable Attribution<Item> consumeCredit(ServerPlayerEntity victim, Identifier deathReason,
                                                            long now) {
        return LEDGER.consume(victim.getUuid(), GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason), now);
    }

    private static @Nullable ServerPlayerEntity pusherOf(ServerPlayerEntity victim, Attribution<Item> credit) {
        return victim.getServer() == null ? null
                : victim.getServer().getPlayerManager().getPlayer(credit.pusherUuid());
    }

    /**
     * One push credit: who pushed, with what, and until which world tick it counts.
     * 一条推人记录：谁推的、用什么推的、在哪个世界刻之前有效。
     */
    record Attribution<W>(UUID pusherUuid, W weapon, long expiresAtTicks) {
    }

    /**
     * The push ledger, generic in the weapon so its window, consumption and cleanup rules are testable without game
     * items. Kill credit is consumed once by a fall; the Judge responsibility copy outlives entity cleanup but uses the
     * same window and is taken once per fall resolution, and never changes who receives the kill reward.
     * 推人账本，对武器类型泛型，使其窗口、消费与清理规则无需游戏物品即可测试。击杀归因被一次坠车消费一次；法官责任副本
     * 不随实体清理消失，但沿用同一窗口并在每次坠车结算时取走一次，从不改变击杀奖励的归属。
     */
    static final class Ledger<W> {
        private final Map<UUID, Attribution<W>> credits = new HashMap<>();
        private final Map<UUID, Attribution<W>> judge = new HashMap<>();

        void record(UUID pusherUuid, UUID targetUuid, W weapon, long currentTime) {
            if (pusherUuid.equals(targetUuid)) {
                return;
            }
            clearExpired(currentTime);
            Attribution<W> attribution = new Attribution<>(pusherUuid, weapon,
                    currentTime + FirePokerRules.FALL_ATTRIBUTION_WINDOW_TICKS);
            credits.put(targetUuid, attribution);
            judge.put(targetUuid, attribution);
        }

        /** Pusher kept for Judge responsibility, removed by every fall resolution. / 供法官责任使用的推人者，每次坠车结算都会取走。 */
        @Nullable UUID takeResponsibleSource(UUID targetUuid, long currentTime) {
            Attribution<W> saved = judge.remove(targetUuid);
            return saved != null && currentTime <= saved.expiresAtTicks() ? saved.pusherUuid() : null;
        }

        /** The fresh credit for a fall death, consumed once; any other death reason leaves it untouched. / 坠车死亡的有效归因，只消费一次。 */
        @Nullable Attribution<W> consume(UUID targetUuid, boolean fallDeath, long currentTime) {
            if (!fallDeath) {
                return null;
            }
            Attribution<W> attribution = credits.remove(targetUuid);
            if (attribution == null || currentTime > attribution.expiresAtTicks()) {
                clearExpired(currentTime);
                return null;
            }
            return attribution;
        }

        void clearPlayer(UUID playerUuid) {
            credits.remove(playerUuid);
            credits.entrySet().removeIf(entry -> entry.getValue().pusherUuid().equals(playerUuid));
            judge.remove(playerUuid);
        }

        void clearAll() {
            credits.clear();
            judge.clear();
        }

        private void clearExpired(long currentTime) {
            judge.entrySet().removeIf(entry -> currentTime > entry.getValue().expiresAtTicks());
            Iterator<Map.Entry<UUID, Attribution<W>>> iterator = credits.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Attribution<W>> entry = iterator.next();
                if (currentTime > entry.getValue().expiresAtTicks()) {
                    iterator.remove();
                }
            }
        }
    }
}
