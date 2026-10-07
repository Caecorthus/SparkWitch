package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

/**
 * Why a gate left the registry (frozen G0; {@link #ADMIN} appended 2026-10-05). Occupants of a closed gate are
 * force-exited at that gate with the re-entry cooldown (C3), except {@link #ROUND_END}, which clears silently without
 * cooldown, and {@link #ADMIN}, which releases them without cooldown.
 * 门离开登记表的原因（G0 冻结；2026-10-05 追加 {@link #ADMIN}）。门内的人会在该门处被强制出门并上再次进门冷却（C3）；
 * 例外是 {@link #ROUND_END}（静默清理、不上冷却）与 {@link #ADMIN}（放出但不上冷却）。
 */
public enum RiftGateCloseReason {
    /** The Riftwalker closed it from the tablet console (D12, P8). / 隙行者在平板控制台关闭（D12，P8）。 */
    CONSOLE,
    /** The entity was lost unexpectedly (chunk unload, foreign discard); registry repair (P1). / 实体意外丢失（P1 修复）。 */
    LOST,
    /** Round start/end sweep; silent, no cooldown. / 对局开始/结束清扫；静默、不上冷却。 */
    ROUND_END,
    /**
     * An operator deleted it with the Rift Gate Remover (owner request 2026-10-05). Occupants are still released at the
     * gate, but a moderation action never charges them the re-entry cooldown.
     * 管理员用传送门清除工具删除（所有者 2026-10-05 要求）。门内的人仍在门处被放出，但管理操作不让他们承担再入冷却。
     */
    ADMIN;

    public boolean appliesExitCooldown() {
        return this != ROUND_END && this != ADMIN;
    }
}
