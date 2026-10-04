package dev.caecorthus.sparkwitch.roles.neutral.fiend;

/**
 * Pure Dash gate (owner decision 2026-10-04): booleans in, a verdict out, no world access. Dash exists only during the
 * Fiend Moment and only for the moment Fiend; {@link FiendDashService} reads every input from server state.
 * 疾驰的纯规则门槛（所有者 2026-10-04 决定）：输入布尔值、输出判定，不访问世界。疾驰只在魔人时刻中、且只对时刻中的
 * 魔人存在；{@link FiendDashService} 从服务端状态读取每个输入。
 */
public final class FiendDashRules {
    /** Outcome of a Dash request. / 疾驰请求的结果。 */
    public enum DashVerdict {
        /** Grant Speed IV and restart the cooldown. / 授予速度 IV 并重新开始冷却。 */
        USE,
        /** Refused silently; costs nothing. / 静默拒绝，不消耗任何东西。 */
        REFUSE,
        /** Refused by Grand Witch Fear; the player is told why. / 被大魔女恐惧拒绝，并提示原因。 */
        FEARED
    }

    private FiendDashRules() {
    }

    /**
     * Dash gate, checked in order: the moment Fiend (role-gated) in an ACTIVE round whose moment is running (active and
     * not yet complete, so a won moment grants nothing), playing and alive, not swallowed by a Taotie, not stunned
     * (Control Expert), not role-skill blocked (SparkTraits), ready; Fear is checked last so its message only appears
     * when Dash would otherwise fire.
     * 疾驰门槛，按顺序检查：处于 ACTIVE 回合、时刻正在进行（已开始且尚未完成，已获胜的时刻不再授予）的时刻中魔人
     * （按职业判断），在局存活，未被饕餮吞下，未被电击眩晕（控场专家），未被职业技能封锁（SparkTraits），已就绪；恐惧
     * 最后检查，使其提示只在本可发动疾驰时出现。
     */
    public static DashVerdict verdict(boolean momentFiend, boolean roundActive, boolean momentRunning,
                                      boolean playingAndAlive, boolean swallowed, boolean stunned,
                                      boolean roleSkillBlocked, boolean dashReady, boolean feared) {
        if (!momentFiend || !roundActive || !momentRunning || !playingAndAlive || swallowed || stunned
                || roleSkillBlocked || !dashReady) {
            return DashVerdict.REFUSE;
        }
        return feared ? DashVerdict.FEARED : DashVerdict.USE;
    }
}
