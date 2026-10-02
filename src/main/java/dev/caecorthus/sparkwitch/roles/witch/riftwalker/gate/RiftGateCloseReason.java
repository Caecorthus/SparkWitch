package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

/**
 * Why a gate left the registry (frozen G0). Occupants of a closed gate are force-exited at that gate with the
 * re-entry cooldown (C3) for every reason except {@link #ROUND_END}, which clears silently without cooldown.
 * 门离开登记表的原因（G0 冻结）。除 {@link #ROUND_END}（静默清理、不上冷却）外，门内的人都会在该门处被强制出门并上
 * 再次进门冷却（C3）。
 */
public enum RiftGateCloseReason {
    /** The Riftwalker closed it from the tablet console (D12, P8). / 隙行者在平板控制台关闭（D12，P8）。 */
    CONSOLE,
    /** The entity was lost unexpectedly (chunk unload, foreign discard); registry repair (P1). / 实体意外丢失（P1 修复）。 */
    LOST,
    /** Round start/end sweep; silent, no cooldown. / 对局开始/结束清扫；静默、不上冷却。 */
    ROUND_END;

    public boolean appliesExitCooldown() {
        return this != ROUND_END;
    }
}
