package dev.caecorthus.sparkwitch.record;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stable cross-mod contract: the SparkWitch-only match-record events that SparkAssist reads (through SparkFactionAPI's
 * round-end broadcast) to award local hidden achievements (achievement record contract W2-W7, 2026-10-09). Event types
 * and field names are frozen; a reader matches them by string. They have no replay formatter on purpose, so Wathe's
 * replay skips them. Every writer runs on the logical server after its feature's own success checks, records nothing
 * outside an active match, and never lets a recording failure reach gameplay. W1 (USEC shot distance and scope) adds
 * fields to the existing {@code sparkwitch:usec_rifle_fire} item use instead; see {@code UsecReplay}.
 * 稳定的跨模组契约：SparkAssist 通过 SparkFactionAPI 回合结束广播读取这些 SparkWitch 专属对局记录事件，用于颁发本地隐藏
 * 成就（成就记录契约 W2-W7，2026-10-09）。事件类型与字段名冻结，读取方按字符串匹配。它们刻意没有回放格式化器，因此
 * Wathe 回放会跳过它们。每个写入方都在逻辑服务端、在其功能自身的成功检查之后执行；对局未激活时不记录；记录失败绝不影响玩法。
 * W1（USEC 射击距离与开镜）则在既有 {@code sparkwitch:usec_rifle_fire} 物品使用记录上追加字段，见 {@code UsecReplay}。
 */
public final class AchievementRecords {
    public static final String WIND_SPIRIT_FALL = "sparkwitch:wind_spirit_fall";
    public static final String SABOTAGE = "sparkwitch:sabotage";
    public static final String CURSE = "sparkwitch:curse";
    public static final String MURDER_SENSE = "sparkwitch:murder_sense";
    public static final String BLACK_RAVEN_PERCEIVED = "sparkwitch:black_raven_perceived";
    public static final String HOLY_FLASH_BURST = "sparkwitch:holy_flash_burst";

    /** Wathe's own actor key, written directly when the actor is offline. / Wathe 自身的发起者键，发起者离线时直接写入。 */
    static final String ACTOR_KEY = "actor";
    static final String LAMPS_KEY = "lamps";
    static final String TARGETS_KEY = "targets";
    static final String DURATION_KEY = "duration";
    static final String ACTOR_FACTION_KEY = "actor_faction";
    static final String ROLE_KEY = "role";
    static final String FACTION_KEY = "faction";
    static final String AFFECTED_KEY = "affected";
    static final String AFFECTED_OTHERS_KEY = "affected_others";

    private AchievementRecords() {
    }

    /**
     * W2: a player died falling out of the train shortly after a promoted Wind Spirit's wind charge caught them. The
     * Wind Spirit may have left; its UUID is then written as {@code actor}. Kill credit is untouched.
     * W2：玩家在被晋升风精灵的风弹击中后不久坠车身亡。风精灵可能已离线，此时直接写入其 UUID 作为 {@code actor}。不改变击杀归属。
     */
    public static void windSpiritFall(ServerPlayerEntity victim, UUID windSpirit) {
        safely(() -> {
            ServerPlayerEntity online = victim.getServer() == null ? null
                    : victim.getServer().getPlayerManager().getPlayer(windSpirit);
            GameRecordManager.EventBuilder event = GameRecordManager.event(WIND_SPIRIT_FALL)
                    .world(victim.getServerWorld())
                    .target(victim);
            actor(event, online, windSpirit).record();
        });
    }

    /** W3: a Saboteur's Sabotage succeeded, with the lamps it turned off (may be 0). / W3：破坏成功及其熄灭的灯数（可为 0）。 */
    public static void sabotage(ServerPlayerEntity saboteur, int lamps) {
        safely(() -> GameRecordManager.event(SABOTAGE).actor(saboteur).putInt(LAMPS_KEY, Math.max(0, lamps)).record());
    }

    /** W4: a Curser's cast succeeded, with the players it cursed. / W4：诅咒师施法成功及其诅咒的玩家数。 */
    public static void curse(ServerPlayerEntity curser, int targets) {
        safely(() -> GameRecordManager.event(CURSE).actor(curser).putInt(TARGETS_KEY, Math.max(0, targets)).record());
    }

    /** W5: an Apprentice Witch activated Murder Sense for {@code durationTicks}. / W5：预备魔女启用杀意感知及其持续刻数。 */
    public static void murderSense(ServerPlayerEntity witch, int durationTicks) {
        safely(() -> GameRecordManager.event(MURDER_SENSE).actor(witch)
                .putInt(DURATION_KEY, Math.max(0, durationTicks)).record());
    }

    /**
     * W6: a Black Raven's Perception revealed {@code target}. Both factions are effective factions at this moment
     * (trait flips such as Conscience included); {@code role} is the role the snapshot revealed.
     * W6：黑鸦的感知揭示了 {@code target}。两个阵营都取此刻的有效阵营（含良知等天赋翻转）；{@code role} 为快照揭示的职业。
     */
    public static void blackRavenPerceived(ServerPlayerEntity raven, ServerPlayerEntity target, Role targetRole,
                                           GameWorldComponent game) {
        safely(() -> {
            if (!GameRecordManager.hasActiveMatch()) {
                return;
            }
            GameRecordManager.EventBuilder event = GameRecordManager.event(BLACK_RAVEN_PERCEIVED)
                    .actor(raven)
                    .target(target)
                    .put(ROLE_KEY, targetRole.identifier().toString());
            putFaction(event, ACTOR_FACTION_KEY, SparkFactionApi.resolveEffectiveFaction(raven, game));
            putFaction(event, FACTION_KEY, SparkFactionApi.resolveEffectiveFaction(target, game));
            event.record();
        });
    }

    /**
     * W7: a Holy Flash went off. {@code thrower} is the owner entity when it is still a server player (dead ones
     * included); otherwise {@code throwerUuid} is written as {@code actor}. Without either, nothing is recorded.
     * W7：圣光弹爆开。投掷者仍是服务端玩家（含已死亡者）时以其为 {@code actor}；否则写入 {@code throwerUuid}。两者皆无时不记录。
     */
    public static void holyFlashBurst(ServerWorld world, @Nullable ServerPlayerEntity thrower,
                                      @Nullable UUID throwerUuid, int affected, int affectedOthers) {
        if (thrower == null && throwerUuid == null) {
            return;
        }
        safely(() -> actor(GameRecordManager.event(HOLY_FLASH_BURST).world(world), thrower, throwerUuid)
                .putInt(AFFECTED_KEY, Math.max(0, affected))
                .putInt(AFFECTED_OTHERS_KEY, Math.max(0, affectedOthers))
                .record());
    }

    private static GameRecordManager.EventBuilder actor(GameRecordManager.EventBuilder event,
                                                        @Nullable ServerPlayerEntity online, @Nullable UUID uuid) {
        if (online != null) {
            return event.actor(online);
        }
        return uuid == null ? event : event.putUuid(ACTOR_KEY, uuid);
    }

    private static void putFaction(GameRecordManager.EventBuilder event, String key, @Nullable Identifier faction) {
        if (faction != null) {
            event.put(key, faction.toString());
        }
    }

    /** A recording failure is logged and swallowed, never thrown into gameplay. / 记录失败只记日志并吞掉，绝不抛进玩法。 */
    private static void safely(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("Failed to write a SparkWitch achievement record", exception);
        }
    }
}
