package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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

    /**
     * True while the cell is converted by a live zone (convert tick <= now < restore tick). Frozen query for the
     * standing check (L3b); L3a implements it.
     * 当该格被存活领域转换时（转换 tick <= 当前 < 恢复 tick）为 true。供站立判定使用的冻结查询（L3b），由 L3a 实现。
     */
    public static boolean isConverted(ServerWorld world, BlockPos pos) {
        return false;
    }

    /** True while the world has at least one live zone. Frozen query (L3b); L3a implements it. / 世界内是否有存活领域。 */
    public static boolean hasActiveZones(ServerWorld world) {
        return false;
    }

    public static void register() {
        // L3 implements: zone ticking, standing checks and the round lifecycle.
        // L3 实现：领域 tick、站立判定与回合生命周期。
    }
}
