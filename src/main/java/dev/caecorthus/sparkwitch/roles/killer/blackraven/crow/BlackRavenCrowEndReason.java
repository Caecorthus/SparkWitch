package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import org.jetbrains.annotations.Nullable;

/**
 * Why a Raven Form session ended. Stable contract for crow-world's {@link BlackRavenCrowEvents#ENDED} listeners: only
 * an end whose {@link #usesHomingFeather()} is true may move the player to a dropped Homing Feather (plan §0); every
 * body-trouble end returns to the body (plan §2.6), and death, round end, disconnect and role loss never teleport.
 * 化鸦会话结束的原因。crow-world 的 {@link BlackRavenCrowEvents#ENDED} 监听器所依赖的稳定契约：只有
 * {@link #usesHomingFeather()} 为真的结束方式可以把玩家传送到已丢出的归羽（计划 §0）；本体出状况的结束一律回到本体
 * （计划 §2.6），死亡、回合结束、断线与失去职业从不传送。
 */
public enum BlackRavenCrowEndReason {
    /** The owner pressed key 2 again. / 主人再次按下技能键 2。 */
    PLAYER(true, true, null),
    /** 30 s elapsed. / 30 秒到时。 */
    TIMEOUT(true, true, "timeout"),
    /** Control Expert stun on the body. / 本体被控场专家眩晕。 */
    STUNNED(false, true, "body"),
    /** Grand Witch Fear on the body. / 本体受大魔女恐惧。 */
    FEARED(false, true, "body"),
    /** Kidnapper control (knocked out and dragged). / 被绑架者控制（击昏拖行）。 */
    KIDNAPPED(false, true, "body"),
    /** Taotie-swallowed body. / 本体被饕餮吞下。 */
    SWALLOWED(false, true, "body"),
    /** Body moved beyond the anchor tolerance, teleported, or lost its footing. / 本体被移动、传送或失去立足。 */
    BODY_MOVED(false, true, "body"),
    /** The crow was hit through the kill link; the owner's own kill follows. / 乌鸦经击杀接口被命中，随后处死主人。 */
    CROW_KILLED(false, true, null),
    /** The crow entity vanished (unloaded, removed, other world). / 乌鸦实体消失。 */
    CROW_LOST(false, true, "body"),
    /** The owner died (any path) or became a spectator. / 主人死亡或成为旁观者。 */
    DIED(false, false, null),
    /** The round left ACTIVE. / 回合离开 ACTIVE。 */
    ROUND_END(false, false, null),
    DISCONNECTED(false, false, null),
    /** The RAW role is no longer the Black Raven. / 原始职业不再是黑羽鸦。 */
    ROLE_LOST(false, false, null);

    private final boolean usesHomingFeather;
    private final boolean startsCooldown;
    private final @Nullable String messageSuffix;

    BlackRavenCrowEndReason(boolean usesHomingFeather, boolean startsCooldown, @Nullable String messageSuffix) {
        this.usesHomingFeather = usesHomingFeather;
        this.startsCooldown = startsCooldown;
        this.messageSuffix = messageSuffix;
    }

    /**
     * A natural end (key 2 or the 30 s timeout): crow-world teleports to a dropped Homing Feather, otherwise the player
     * simply is back in the body.
     * 自然结束（技能键 2 或 30 秒到时）：若已丢出归羽，crow-world 传送到归羽；否则玩家直接回到本体。
     */
    public boolean usesHomingFeather() {
        return usesHomingFeather;
    }

    /** Whether the 45 s cooldown starts (the round goes on for this player). / 是否开始 45 秒冷却（该玩家的回合仍在继续）。 */
    public boolean startsCooldown() {
        return startsCooldown;
    }

    /** Owner action-bar key, or null when silent. / 主人动作栏提示键；静默时为 null。 */
    public @Nullable String messageKey() {
        return messageSuffix == null ? null : "message.sparkwitch.black_raven_crow.ended." + messageSuffix;
    }
}
