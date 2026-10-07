package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

/**
 * Why a Rift session ended (frozen G0; P2 implements each path, modelled on {@code SeekerExitReason} and research 03
 * §4.3). Every exit except {@link #ROUND_END} starts the re-entry cooldown (D14, C2, C3); whether the body is
 * teleported and its game mode restored is P2's per-reason rule (never revive a dead body).
 * Rift 会话结束的原因（G0 冻结；由 P2 实现各路径，参照 {@code SeekerExitReason} 与调研 03 §4.3）。除 {@link #ROUND_END}
 * 外，所有出门都会开始再次进门冷却（D14、C2、C3）；是否传送本体、是否恢复游戏模式由 P2 按原因决定（绝不复活尸体）。
 */
public enum RiftExitReason {
    /** The occupant pressed Shift (fresh press after entry). / 门内的人按下 Shift（进门后的新按键）。 */
    PLAYER,
    /** Stay limit reached (D5b): exit at the CURRENT gate (C2). / 达到停留上限（D5b）：在当前所在的门出门（C2）。 */
    STAY_EXPIRED,
    /** The current gate was closed or lost (C3): exit at that gate. / 当前所在的门被关闭或丢失（C3）：在该门处出门。 */
    GATE_CLOSED,
    /** Role or eligibility changed (role reassignment, faction change). / 职业或资格变化。 */
    INELIGIBLE,
    /** A foreign force moved the anchored body (backstop); keep the new position. / 外力挪动了锚定的本体（兜底）；保留新位置。 */
    BODY_MOVED,
    /** Another authority took the body (Taotie swallow, Last Stand, Depression); never touch game mode. / 其他机制接管本体。 */
    INTERCEPTED,
    /** Death; clear state only, never touch game mode. / 死亡；只清状态，不动游戏模式。 */
    DIED,
    /** Disconnect; clear the session only (Wathe then kills as ESCAPED). / 断线；只清会话（随后 Wathe 以 ESCAPED 处死）。 */
    DISCONNECTED,
    /** Round start/end or reset; silent, no cooldown. / 对局开始/结束或重置；静默、不上冷却。 */
    ROUND_END;

    public boolean appliesCooldown() {
        return this != ROUND_END;
    }
}
