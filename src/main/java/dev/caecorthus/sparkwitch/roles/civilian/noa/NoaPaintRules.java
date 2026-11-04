package dev.caecorthus.sparkwitch.roles.civilian.noa;

/**
 * Pure painting gate: booleans in, a verdict out, no world access. {@link NoaPaintService} reads every input from
 * server state.
 * 作画的纯规则门槛：输入布尔值、输出判定，不访问世界。{@link NoaPaintService} 从服务端状态读取每个输入。
 */
public final class NoaPaintRules {
    /** Outcome of a painting request. / 作画请求的结果。 */
    public enum Verdict {
        /** Paint and restart the cooldown. / 作画并重新开始冷却。 */
        USE,
        /** Refused silently; costs nothing. / 静默拒绝，不消耗任何东西。 */
        REFUSE,
        /** Refused by Grand Witch Fear; the player is told why. / 被大魔女恐惧拒绝，并提示原因。 */
        FEARED
    }

    private NoaPaintRules() {
    }

    /**
     * Painting gate, checked in order (the Blind Attune set): the real Noa in an ACTIVE round, playing, alive and not a
     * spectator, not swallowed by a Taotie, not stunned by the Control Expert, not role-skill blocked (SparkTraits),
     * ready; Fear is checked last so its message only appears when the painting would otherwise happen. The Seeker,
     * Riftwalker and Knight duel sessions drop the payload before it gets here.
     * 作画门槛，按顺序检查（与盲人凝神相同）：处于 ACTIVE 回合的真实诺亚，在局存活且不是旁观者，未被饕餮吞下，未被控场专家
     * 眩晕，未被职业技能封锁（SparkTraits），已就绪；恐惧最后检查，使其提示只在本可作画时出现。搜寻者、隙行者与骑士决斗
     * 会话会在此之前丢弃数据包。
     */
    public static Verdict verdict(boolean noa, boolean roundActive, boolean playingAndAlive, boolean swallowed,
                                  boolean stunned, boolean roleSkillBlocked, boolean ready, boolean feared) {
        if (!noa || !roundActive || !playingAndAlive || swallowed || stunned || roleSkillBlocked || !ready) {
            return Verdict.REFUSE;
        }
        return feared ? Verdict.FEARED : Verdict.USE;
    }
}
