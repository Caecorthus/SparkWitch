package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

/**
 * Deep Dark Zone runtime: client-only fake sculk blocks (the server world never changes), standing effects, the
 * exposure mark and lifecycle cleanup. Server-authoritative.
 * Frozen stub (L0): no-op until L3 implements it.
 * 深暗领域运行时：仅发给客户端的假幽匿方块（服务端世界从不改变）、站立效果、暴露标记与生命周期清理。由服务端权威决定。
 * 冻结桩（L0）：在 L3 实现之前不做任何事。
 */
public final class DeepDarkZoneService {
    private DeepDarkZoneService() {
    }

    public static void register() {
        // L3 implements: zone ticking, standing checks and the round lifecycle.
        // L3 实现：领域 tick、站立判定与回合生命周期。
    }
}
