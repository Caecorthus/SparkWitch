package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Abyss Listener bound kit: grants the Shriek Gun after a committed recruitment, at round start for a forced Abyss
 * Listener, and keeps it reconciled (restore without resetting the cooldown, revoke from non-holders). Server-only.
 * Frozen stub (L0): no-op until L2 implements it.
 * 聆渊者绑定装备：招募提交后、被强制指定的聆渊者开局时发放啸音铳，并持续校正（补发但不重置冷却，收回不该持有者的）。
 * 仅服务端。冻结桩（L0）：在 L2 实现之前不做任何事。
 */
public final class AbyssListenerLoadout {
    private AbyssListenerLoadout() {
    }

    public static void register() {
        // L2 implements: round-start grant and the periodic reconcile.
        // L2 实现：开局发放与周期性校正。
    }

    /**
     * Called by the accomplice-variant hook once the recruitment committed (inventory already restored).
     * 招募提交后（背包已恢复）由共犯变体回调调用。
     */
    public static void grantAfterRecruit(ServerPlayerEntity recruit) {
        // L2 implements: grant the bound Shriek Gun with its initial cooldown.
        // L2 实现：发放绑定的啸音铳并写入首次冷却。
    }
}
