package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pure Fiend Moment decisions (purchase, win, lifecycle): booleans in, decisions out, no world access.
 * 魔人时刻的纯规则判断（购买、胜负、生命周期）：输入布尔值、输出决定，不访问世界。
 */
public final class FiendMomentRules {
    /** Replay global events, each with a registered formatter. / 回放全局事件，均注册了专属格式化器。 */
    public static final Identifier START_EVENT_ID = SparkWitch.id("fiend_moment_start");
    public static final Identifier END_EVENT_ID = SparkWitch.id("fiend_moment_end");

    /**
     * What the early-phase win listener returns while it runs before every other listener (D2).
     * 早期阶段胜负监听器在所有其他监听器之前运行时的返回决定（D2）。
     */
    public enum WinDecision {
        /** No moment: {@code null}, other listeners decide. / 无时刻：返回 {@code null}，交由其他监听器。 */
        ABSTAIN,
        /** Moment invalid: end it, then {@code null}. / 时刻失效：结束时刻后返回 {@code null}。 */
        END,
        /** Moment running: every other win is suspended. / 时刻进行中：暂停所有其他胜利。 */
        BLOCK,
        /** Moment survived: the Fiend wins alone. / 撑过时刻：魔人独自获胜。 */
        WIN
    }

    /** How a moment ends, what everyone is told, and whether the Fiend is spent. / 时刻如何结束、向所有人公布什么，以及魔人是否耗尽。 */
    public enum EndReason {
        /** The moment Fiend died: "the Fiend fell" title and replay line. / 时刻中的魔人死亡：公布「魔人已被击杀」并记录回放。 */
        DIED(true, true, false),
        /** Role change or the Fiend left: "moment ended" title and replay line. / 职业变更或魔人离开：公布「魔人时刻已结束」并记录回放。 */
        ENDED(true, true, false),
        /**
         * A Taotie swallowed the moment Fiend: "moment ended" title, replay line, no win, and the Fiend is spent for the
         * rest of the match, even after a release.
         * 饕餮吞噬了时刻中的魔人：公布「魔人时刻已结束」并记录回放，不获胜，且该魔人在本对局剩余时间内耗尽，即使之后被释放。
         */
        SWALLOWED(true, true, true),
        /** Round boundary, stale match, or a death after the deadline: no announcement, no replay. / 回合边界、过期对局或截止后的死亡：不公布、不记录。 */
        SILENT(false, false, false);

        private final boolean announces;
        private final boolean recordsReplay;
        private final boolean marksSpent;

        EndReason(boolean announces, boolean recordsReplay, boolean marksSpent) {
            this.announces = announces;
            this.recordsReplay = recordsReplay;
            this.marksSpent = marksSpent;
        }

        public boolean announces() {
            return announces;
        }

        public boolean recordsReplay() {
            return recordsReplay;
        }

        public boolean marksSpent() {
            return marksSpent;
        }
    }

    private FiendMomentRules() {
    }

    /**
     * Purchase eligibility; any false means no charge. / 购买资格；任一不满足即不扣费。
     */
    public static boolean canStart(boolean dormantFiend, boolean gameActive, @Nullable UUID matchId,
                                   boolean momentActive) {
        return dormantFiend && gameActive && matchId != null && !momentActive;
    }

    /**
     * Why an active moment is no longer valid, or {@code null} while it is. Checked in this order: stale match
     * (silent), Fiend offline, Fiend dead, Fiend swallowed by a Taotie (out of play, spent), Fiend no longer holds the
     * role.
     * 进行中的时刻为何失效；仍有效时返回 {@code null}。检查顺序：对局已变更（静默）、魔人离线、魔人死亡、魔人被饕餮吞噬
     * （退出对局并耗尽）、魔人不再持有该职业。
     */
    public static @Nullable EndReason invalidation(boolean sameMatch, boolean online, boolean alive, boolean swallowed,
                                                   boolean fiendRole) {
        if (!sameMatch) {
            return EndReason.SILENT;
        }
        if (!online) {
            return EndReason.ENDED;
        }
        if (!alive) {
            return EndReason.DIED;
        }
        if (swallowed) {
            return EndReason.SWALLOWED;
        }
        return fiendRole ? null : EndReason.ENDED;
    }

    /** Decision table of the win listener. / 胜负监听器的决策表。 */
    public static WinDecision decide(boolean momentActive, @Nullable EndReason invalidation, boolean complete) {
        if (!momentActive) {
            return WinDecision.ABSTAIN;
        }
        if (invalidation != null) {
            return WinDecision.END;
        }
        return complete ? WinDecision.WIN : WinDecision.BLOCK;
    }

    /** A final (not intercepted) death of the moment Fiend ends the moment. / 时刻中魔人的最终死亡（未被拦截）结束时刻。 */
    public static boolean endsOnDeath(boolean momentFiend, boolean deathIntercepted) {
        return momentFiend && !deathIntercepted;
    }

    /**
     * A death on or after the deadline is not announced: the Fiend has already won or the win listener settles it.
     * A disconnect ({@code wathe:escaped}) is not a kill: it ends the moment as "ended", never "slain" (C15).
     * 截止当刻或之后的死亡不公布：魔人已获胜，或由胜负监听器结算。断线（{@code wathe:escaped}）不是击杀：时刻以
     * 「已结束」而非「已被击杀」结束（C15）。
     */
    public static EndReason deathEnd(boolean complete, boolean escaped) {
        if (complete) {
            return EndReason.SILENT;
        }
        return escaped ? EndReason.ENDED : EndReason.DIED;
    }

    /**
     * The granted crowbar is taken back on every end except the win: a completed moment closed silently at the round
     * end (a death after the deadline shares that path, where the crowbar is irrelevant) (C15).
     * 除获胜外，每种结束都会收回授予的撬棍；获胜即已完成的时刻在回合结束时被静默关闭（截止后的死亡也走此路径，撬棍已无关紧要）（C15）。
     */
    public static boolean takesCrowbarBack(EndReason reason, boolean complete) {
        return !(complete && reason == EndReason.SILENT);
    }

    /** Any new role other than the Fiend ends the moment. / 任何非魔人的新职业都会结束时刻。 */
    public static boolean endsOnRoleAssigned(boolean momentFiend, boolean newRoleIsFiend) {
        return momentFiend && !newRoleIsFiend;
    }

    /**
     * A player reset clears the moment when it is the moment Fiend's, or at a round boundary (game not ACTIVE).
     * 玩家重置时，若为时刻中的魔人本人，或处于回合边界（对局非 ACTIVE），则清除时刻。
     */
    public static boolean clearsOnReset(boolean momentActive, boolean momentFiend, boolean gameActive) {
        return momentActive && (momentFiend || !gameActive);
    }

    /**
     * The moment owns the live Speed instance only when its own grant decided the merged result.
     * 只有当时刻自身的授予决定了合并结果时，时刻才拥有当前的速度效果实例。
     */
    public static boolean claimsSpeed(int liveAmplifier, int liveDuration) {
        return liveAmplifier == FiendRules.MOMENT_SPEED_AMPLIFIER
                && liveDuration == FiendRules.MOMENT_DURATION_TICKS;
    }
}
