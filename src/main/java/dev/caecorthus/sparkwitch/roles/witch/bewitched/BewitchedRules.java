package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Bewitched (魔化使) constants and pure predicates. A Bewitched is a witch-faction member dealt at round start in a
 * Grand Witch round (D1); it only does tasks, and after {@link #PROMOTION_TASKS} completed tasks it is promoted to an
 * accomplice role rolled from the special-accomplice pool (D3). Server-authoritative; the client only mirrors the
 * owner-synced task count.
 * 魔化使的常量与纯判定。魔化使是有大魔女的对局开局时发放的魔女阵营成员（D1）；它只能做任务，完成
 * {@link #PROMOTION_TASKS} 个任务后晋升为从特殊共犯池抽取的共犯身份（D3）。服务端权威；客户端只镜像同步给本人的任务数。
 */
public final class BewitchedRules {
    public static final Identifier ROLE_ID = SparkWitch.id("bewitched");
    /** Role theme color (dusky violet, C1). / 职业主题色（暗紫，C1）。 */
    public static final int COLOR = 0x8A6E99;
    /** Completed tasks that unlock the promotion (owner request). / 解锁晋升所需完成的任务数（所有者要求）。 */
    public static final int PROMOTION_TASKS = 2;
    /** Replay cause of the promotion role change. / 晋升身份变更的回放原因。 */
    public static final Identifier PROMOTION_CAUSE = SparkWitch.id("bewitched_promotion");

    private BewitchedRules() {
    }

    /** Exactly the Bewitched (real role). / 恰为魔化使（真实身份）。 */
    public static boolean isBewitched(@Nullable Role role) {
        return WitchFactionRules.isBewitched(role);
    }

    /** Task count after one more completed task, capped at {@link #PROMOTION_TASKS}. / 再完成一个任务后的计数（封顶）。 */
    public static int nextTaskCount(int current) {
        return Math.min(PROMOTION_TASKS, Math.max(0, current) + 1);
    }

    /**
     * True only for the task that reaches the threshold, so the promotion is queued exactly once.
     * 仅在恰好达到门槛的那个任务时为真，因此晋升只会入队一次。
     */
    public static boolean reachesPromotion(int previous, int next) {
        return previous < PROMOTION_TASKS && next >= PROMOTION_TASKS;
    }

    /**
     * D2: a living Bewitched sees its balance even though its shop is empty (an empty shop would hide it in Wathe's
     * default). Anyone else is left to later listeners.
     * D2：存活的魔化使即使商店为空也能看到余额（Wathe 默认在商店为空时隐藏余额）。其他人交给后续监听器。
     */
    public static @Nullable CanSeeMoney.Result moneyVisibility(boolean livingParticipant, boolean bewitched) {
        return livingParticipant && bewitched ? CanSeeMoney.Result.ALLOW : null;
    }

    /** What the end-of-tick queue does with one pending UUID. / 刻末队列如何处理一个待晋升 UUID。 */
    public enum QueueAction {
        /** Change the role now. / 立即变更身份。 */
        PROMOTE,
        /** Keep it queued and retry next tick. / 保留在队列中，下一刻重试。 */
        DEFER,
        /** Stale entry: forget it. / 过期条目：丢弃。 */
        DROP
    }

    /**
     * Pure queue decision (C3). Offline holders stay queued. A promotion needs an ACTIVE round (never once the win is
     * decided and Wathe is STOPPING), a living participant whose real role is still the Bewitched and a full task
     * count; it is deferred while the player cannot safely change role: inside a Rift Gate (an alive spectator held by
     * the session), swallowed by a NoellesRoles Taotie, hijacked by a Kidnapper, or afflicted by a Hunter trap (root,
     * fracture or trap poison). The Kidnapper and Hunter {@code RoleAssigned} listeners reset that victim-side state
     * for any role change, so promoting then would end the hijack or heal the injury and drop the Hunter's poison
     * credit. Each of these states ends on its own.
     * 纯队列判定（C3）。离线者保留在队列中。晋升要求对局处于 ACTIVE（胜负已定、Wathe 进入 STOPPING 后不再晋升）、参与且存活、
     * 真实身份仍为魔化使且任务数已满；玩家无法安全变更身份时延后：位于裂隙门内（被会话托管的存活旁观者）、被 NoellesRoles
     * 饕餮吞下、被绑架者劫持，或受猎人陷阱影响（定身、骨折或陷阱毒）。绑架者与猎人的 {@code RoleAssigned} 监听器会在任何
     * 身份变更时重置受害者一侧的状态，此时晋升会提前结束劫持或治愈伤势并丢失猎人的毒杀归属。这些状态都会自行结束。
     */
    public static QueueAction queueAction(boolean online, boolean roundActive, boolean playingAndAlive,
                                          boolean bewitched, int promotionTasks, boolean insideRiftGate,
                                          boolean swallowed, boolean kidnapperControlled, boolean hunterAfflicted) {
        if (!online) {
            return QueueAction.DEFER;
        }
        if (!roundActive || !playingAndAlive || !bewitched || promotionTasks < PROMOTION_TASKS) {
            return QueueAction.DROP;
        }
        return insideRiftGate || swallowed || kidnapperControlled || hunterAfflicted
                ? QueueAction.DEFER
                : QueueAction.PROMOTE;
    }
}
