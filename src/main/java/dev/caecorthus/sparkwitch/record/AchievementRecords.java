package dev.caecorthus.sparkwitch.record;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stable cross-mod contract: the SparkWitch-only match-record events that SparkAssist reads (through SparkFactionAPI's
 * round-end broadcast) to award local hidden achievements (achievement record contract W2-W7, 2026-10-09; W8-W15 and
 * W17, 2026-10-14). Event types and field names are frozen; a reader matches them by string. They have no replay
 * formatter on purpose, so Wathe's replay skips them. Every writer runs on the logical server after its feature's own
 * success checks, records nothing outside an active match, and never lets a recording failure reach gameplay. W1 (USEC
 * shot distance and scope) adds fields to the existing {@code sparkwitch:usec_rifle_fire} item use instead; see
 * {@code UsecReplay}. W16 (Bone Setting absorbed a trap fracture) likewise adds a field to the existing
 * {@code sparkwitch:hunter_trap_triggered} global event; see {@code HunterTrapEntity}.
 * 稳定的跨模组契约：SparkAssist 通过 SparkFactionAPI 回合结束广播读取这些 SparkWitch 专属对局记录事件，用于颁发本地隐藏
 * 成就（成就记录契约 W2-W7，2026-10-09；W8-W15 与 W17，2026-10-14）。事件类型与字段名冻结，读取方按字符串匹配。它们刻意
 * 没有回放格式化器，因此 Wathe 回放会跳过它们。每个写入方都在逻辑服务端、在其功能自身的成功检查之后执行；对局未激活时不记录；
 * 记录失败绝不影响玩法。W1（USEC 射击距离与开镜）则在既有 {@code sparkwitch:usec_rifle_fire} 物品使用记录上追加字段，见
 * {@code UsecReplay}。W16（正骨吸收了捕兽夹骨折）同样在既有 {@code sparkwitch:hunter_trap_triggered} 全局事件上追加字段，
 * 见 {@code HunterTrapEntity}。
 */
public final class AchievementRecords {
    public static final String WIND_SPIRIT_FALL = "sparkwitch:wind_spirit_fall";
    public static final String SABOTAGE = "sparkwitch:sabotage";
    public static final String CURSE = "sparkwitch:curse";
    public static final String MURDER_SENSE = "sparkwitch:murder_sense";
    public static final String BLACK_RAVEN_PERCEIVED = "sparkwitch:black_raven_perceived";
    public static final String HOLY_FLASH_BURST = "sparkwitch:holy_flash_burst";
    public static final String NINJA_PARRY = "sparkwitch:ninja_parry";
    public static final String POISON_APPLE_TAKE = "sparkwitch:poison_apple_take";
    public static final String PERFUME_MARK = "sparkwitch:perfume_mark";
    public static final String PROPHECY = "sparkwitch:prophecy";
    public static final String SAINT_KARMA = "sparkwitch:saint_karma";
    public static final String JUDGE_VERDICT = "sparkwitch:judge_verdict";
    public static final String EMMA_HUNT_REWARD = "sparkwitch:emma_hunt_reward";
    public static final String SEEKER_MARK = "sparkwitch:seeker_mark";
    public static final String MAGICIAN_PUPPET_KILL = "sparkwitch:magician_puppet_kill";

    /** Wathe's own actor key, written directly when the actor is offline. / Wathe 自身的发起者键，发起者离线时直接写入。 */
    static final String ACTOR_KEY = "actor";
    /** Wathe's own target key, written directly when the target is offline. / Wathe 自身的目标键，目标离线时直接写入。 */
    static final String TARGET_KEY = "target";
    static final String LAMPS_KEY = "lamps";
    static final String TARGETS_KEY = "targets";
    static final String DURATION_KEY = "duration";
    static final String ACTOR_FACTION_KEY = "actor_faction";
    static final String ROLE_KEY = "role";
    static final String FACTION_KEY = "faction";
    static final String AFFECTED_KEY = "affected";
    static final String AFFECTED_OTHERS_KEY = "affected_others";
    static final String DEATH_REASON_KEY = "death_reason";
    static final String WEAPON_KEY = "weapon";
    static final String POISONED_KEY = "poisoned";
    static final String GUESS_KEY = "guess";
    static final String CORRECT_KEY = "correct";
    static final String ATTEMPT_KEY = "attempt";
    static final String KILLER_KEY = "killer";
    static final String KILLER_FACTION_KEY = "killer_faction";
    static final String SENTENCED_KEY = "sentenced";
    static final String KILLS_KEY = "kills";
    static final String REQUIRED_KILLS_KEY = "required_kills";
    static final String DISGUISE_PLAYER_KEY = "disguise_player";
    static final String DISGUISE_DEAD_KEY = "disguise_dead";

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

    /**
     * W8: a Ninja's parry blocked {@code attacker}'s killing blow. {@code death_reason} is the blocked death reason and
     * {@code weapon} the attacker's main-hand item id at that moment ({@code minecraft:air} when empty). Never written
     * for a Ceremonial Sword blow: it pierces the parry and still kills.
     * W8：忍者的格挡挡下了 {@code attacker} 的致命一击。{@code death_reason} 为被挡下的死因，{@code weapon} 为此刻攻击者主手
     * 物品标识（空手为 {@code minecraft:air}）。仪礼剑会穿透格挡并照常击杀，因此绝不为其写入。
     */
    public static void ninjaParry(ServerPlayerEntity ninja, ServerPlayerEntity attacker, Identifier deathReason) {
        safely(() -> GameRecordManager.event(NINJA_PARRY).actor(ninja).target(attacker)
                .put(DEATH_REASON_KEY, deathReason.toString())
                .put(WEAPON_KEY, Registries.ITEM.getId(attacker.getMainHandStack().getItem()).toString())
                .record());
    }

    /**
     * W9: {@code taker} took a serving from a platter armed with a Poison Apple. {@code actor} is the player who armed
     * it (UUID when offline); {@code poisoned} is true only for the poisoned serving (the second take; the first is
     * safe). The poisoned serving still has to be eaten or drunk, by whoever ends up holding it, to poison anyone.
     * W9：{@code taker} 从布置了毒苹果的餐盘上取走一份。{@code actor} 为布置者（离线时写入其 UUID）；仅被下毒的那一份
     * （第二次拿取；第一次是安全的）的 {@code poisoned} 为 true。被下毒的那一份仍需被最终持有者吃下或喝下才会使人中毒。
     */
    public static void poisonAppleTake(ServerPlayerEntity taker, UUID poisoner, boolean poisoned) {
        safely(() -> {
            GameRecordManager.EventBuilder event = GameRecordManager.event(POISON_APPLE_TAKE)
                    .world(taker.getServerWorld())
                    .target(taker)
                    .putBool(POISONED_KEY, poisoned);
            actor(event, online(taker, poisoner), poisoner).record();
        });
    }

    /** W10: a Perfumer's Perfume Essence marked {@code target}. / W10：调香师的香精标记了 {@code target}。 */
    public static void perfumeMark(ServerPlayerEntity perfumer, ServerPlayerEntity target) {
        safely(() -> GameRecordManager.event(PERFUME_MARK).actor(perfumer).target(target).record());
    }

    /**
     * W11: a Prophet's charged Prophecy about the dead {@code victim} (written as {@code target}, UUID when offline).
     * {@code guess} is the guessed cause group id, {@code correct} whether it matched, and {@code attempt} the 1-based
     * guess number on this death of that victim (a revived victim's new death starts again at 1). When a correct guess
     * names someone, {@code killer} is the revealed responsible player and {@code killer_faction} their effective
     * faction now (the role's faction when offline); both are absent otherwise.
     * W11：先知针对死者 {@code victim} 的一次已扣费预言（写入为 {@code target}，离线时写入 UUID）。{@code guess} 为所猜死因
     * 分组 id，{@code correct} 为是否猜中，{@code attempt} 为针对该死者这一次死亡的第几次猜测（从 1 起；被复活者再次死亡后
     * 重新从 1 计）。猜中且揭示了某人时，{@code killer} 为被揭示的责任人，{@code killer_faction} 为其此刻的有效阵营（离线时
     * 取其职业阵营）；否则两者都不写入。
     */
    public static void prophecy(ServerPlayerEntity prophet, UUID victim, String guess, boolean correct, int attempt,
                                @Nullable UUID killer) {
        safely(() -> {
            if (!GameRecordManager.hasActiveMatch()) {
                return;
            }
            GameRecordManager.EventBuilder event = GameRecordManager.event(PROPHECY)
                    .actor(prophet)
                    .put(GUESS_KEY, guess)
                    .putBool(CORRECT_KEY, correct)
                    .putInt(ATTEMPT_KEY, Math.max(1, attempt));
            target(event, online(prophet, victim), victim);
            if (killer != null) {
                event.putUuid(KILLER_KEY, killer);
                putFaction(event, KILLER_FACTION_KEY, factionOf(prophet, killer));
            }
            event.record();
        });
    }

    /**
     * W12: a Saint died with Hellfire active and {@code killer} took Karma (it passed the Karma immunity check).
     * {@code actor} is the dead Saint.
     * W12：圣徒在业火状态下死亡，{@code killer} 背上了业障（已通过业障免疫检查）。{@code actor} 为死去的圣徒。
     */
    public static void saintKarma(ServerPlayerEntity saint, ServerPlayerEntity killer) {
        safely(() -> GameRecordManager.event(SAINT_KARMA).actor(saint).target(killer).record());
    }

    /**
     * W13: a Judge's charged judgment of {@code defendant}, after the fee was taken. {@code sentenced} is whether the
     * defendant's {@code kills} reached {@code required_kills}; {@code duration} is the sentence in ticks (0 when not
     * sentenced).
     * W13：法官对 {@code defendant} 的一次已扣费审判（扣费之后写入）。{@code sentenced} 为被告的 {@code kills} 是否达到
     * {@code required_kills}；{@code duration} 为刑期刻数（未判刑时为 0）。
     */
    public static void judgeVerdict(ServerPlayerEntity judge, ServerPlayerEntity defendant, boolean sentenced,
                                    int kills, int requiredKills, int durationTicks) {
        safely(() -> GameRecordManager.event(JUDGE_VERDICT).actor(judge).target(defendant)
                .putBool(SENTENCED_KEY, sentenced)
                .putInt(KILLS_KEY, Math.max(0, kills))
                .putInt(REQUIRED_KILLS_KEY, Math.max(0, requiredKills))
                .putInt(DURATION_KEY, sentenced ? Math.max(0, durationTicks) : 0)
                .record());
    }

    /**
     * W14: Emma's revolver killed the Witch Factor holder {@code factorHolder} and earned the hunt reward (the speed
     * burst and the revolver cooldown cut to a tenth, about 1 s).
     * W14：艾玛的左轮击杀了魔女因子持有者 {@code factorHolder}，获得猎杀奖励（加速与降至十分之一、约 1 秒的左轮冷却）。
     */
    public static void emmaHuntReward(ServerPlayerEntity emma, ServerPlayerEntity factorHolder) {
        safely(() -> GameRecordManager.event(EMMA_HUNT_REWARD).actor(emma).target(factorHolder).record());
    }

    /**
     * W15: a Seeker's device was broken and {@code breaker} was marked for {@code duration} ticks.
     * W15：搜寻者的设备被打坏，{@code breaker} 被标记 {@code duration} 刻。
     */
    public static void seekerMark(ServerPlayerEntity seeker, ServerPlayerEntity breaker, int durationTicks) {
        safely(() -> GameRecordManager.event(SEEKER_MARK).actor(seeker).target(breaker)
                .putInt(DURATION_KEY, Math.max(0, durationTicks)).record());
    }

    /**
     * W17: a Magician's playback puppet killed {@code victim}, and the kill took. {@code weapon} is the puppet's weapon
     * item id ({@code wathe:knife}, {@code wathe:bat} or {@code wathe:revolver}); {@code disguise_player} is the player
     * the puppet was disguised as (absent when none) and {@code disguise_dead} whether that player was dead just before
     * the kill.
     * W17：魔术师的回放皮套击杀了 {@code victim}，且击杀生效。{@code weapon} 为皮套武器的物品标识（{@code wathe:knife}、
     * {@code wathe:bat} 或 {@code wathe:revolver}）；{@code disguise_player} 为皮套伪装的玩家（无伪装时不写入），
     * {@code disguise_dead} 为该玩家在这次击杀前是否已死亡。
     */
    public static void magicianPuppetKill(ServerPlayerEntity magician, ServerPlayerEntity victim, Identifier weapon,
                                          @Nullable UUID disguise, boolean disguiseDead) {
        safely(() -> {
            GameRecordManager.EventBuilder event = GameRecordManager.event(MAGICIAN_PUPPET_KILL)
                    .actor(magician)
                    .target(victim)
                    .put(WEAPON_KEY, weapon.toString())
                    .putBool(DISGUISE_DEAD_KEY, disguise != null && disguiseDead);
            if (disguise != null) {
                event.putUuid(DISGUISE_PLAYER_KEY, disguise);
            }
            event.record();
        });
    }

    private static @Nullable ServerPlayerEntity online(ServerPlayerEntity context, UUID player) {
        return context.getServer() == null ? null : context.getServer().getPlayerManager().getPlayer(player);
    }

    private static GameRecordManager.EventBuilder target(GameRecordManager.EventBuilder event,
                                                         @Nullable ServerPlayerEntity online, UUID uuid) {
        return online != null ? event.target(online) : event.putUuid(TARGET_KEY, uuid);
    }

    /**
     * The effective faction of {@code player}: resolved on the player while online, else from the round role.
     * {@code player} 的有效阵营：在线时按玩家解析，否则按其本局职业解析。
     */
    private static @Nullable Identifier factionOf(ServerPlayerEntity context, UUID player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(context.getServerWorld());
        ServerPlayerEntity online = online(context, player);
        if (online != null) {
            return SparkFactionApi.resolveEffectiveFaction(online, game);
        }
        Role role = game.getRole(player);
        return role == null ? null : SparkFactionApi.resolveEffectiveFaction(role);
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
