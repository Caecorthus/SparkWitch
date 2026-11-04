package dev.caecorthus.sparkwitch.roles.civilian.athlete;

/**
 * Pure dash gate: booleans in, a verdict out, no world access. {@link AthleteDashService} reads every input from server
 * state; the destination search runs only after {@link DashVerdict#USE}.
 * 冲刺的纯规则门槛：输入布尔值、输出判定，不访问世界。{@link AthleteDashService} 从服务端状态读取每个输入；只有判定为
 * {@link DashVerdict#USE} 后才搜索落点。
 */
public final class AthleteDashRules {
    /** Outcome of a dash request. / 冲刺请求的结果。 */
    public enum DashVerdict {
        /** Search a destination; a found one teleports and restarts the cooldown. / 搜索落点；找到即传送并重新开始冷却。 */
        USE,
        /** Refused silently; costs nothing. / 静默拒绝，不消耗任何东西。 */
        REFUSE,
        /** Refused by Grand Witch Fear (owner 2026-11-03); the player is told why. / 被大魔女恐惧拒绝（所有者 2026-11-03），并提示原因。 */
        FEARED
    }

    private AthleteDashRules() {
    }

    /**
     * Dash gate, checked in order: the real Athlete in an ACTIVE round, playing, alive and not a spectator, not held
     * (swallowed, Last Stand, Last Escape, Kidnapper control, Control Expert stun, Seeker session, borrowed camera or
     * Hunter root), not riding or sleeping (a teleport would dismount or wake them), not role-skill blocked
     * (SparkTraits), ready; Fear is checked last so its message only appears when the dash would otherwise fire.
     * 冲刺门槛，按顺序检查：处于 ACTIVE 回合的真实运动员，在局存活且不是旁观者，未被持有（饕餮吞下、背水一战、脱险、
     * 绑架者控制、控场专家眩晕、搜寻者会话、镜头被占用或被猎人定身），未骑乘或睡觉（传送会让其下坐骑或醒来），未被
     * 职业技能封锁（SparkTraits），已就绪；恐惧最后检查，使其提示只在本可冲刺时出现。
     */
    public static DashVerdict verdict(boolean athlete, boolean roundActive, boolean playingAndAlive, boolean held,
                                      boolean ridingOrSleeping, boolean roleSkillBlocked, boolean dashReady,
                                      boolean feared) {
        if (!athlete || !roundActive || !playingAndAlive || held || ridingOrSleeping || roleSkillBlocked
                || !dashReady) {
            return DashVerdict.REFUSE;
        }
        return feared ? DashVerdict.FEARED : DashVerdict.USE;
    }
}
