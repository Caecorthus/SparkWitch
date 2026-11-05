package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Frozen contract (server only): the single entry point that bursts, carves and clears BEAR smoke. The smoke round
 * entity, explosions and the round lifecycle call only these methods; the shape, caps and client sync live behind it.
 * SPIKE STUB: the shape package replaces the bodies.
 * 冻结契约（仅服务端）：BEAR 烟雾爆开、开洞与清除的唯一入口。烟雾榴弹实体、各种爆炸与回合生命周期只调用这些方法；
 * 形状、上限与客户端同步都在其后。原型占位：由形状工作包替换实现。
 */
public final class SmokeCloudService {
    private SmokeCloudService() {
    }

    /**
     * Bursts a cloud at {@code impact}. {@code incomingDirection} (may be null) is the round's flight direction and is
     * used to step the seed back out of a block it hit. Returns the new cloud id, or -1 when nothing was spawned.
     * 在 {@code impact} 处爆开一团烟。{@code incomingDirection}（可为 null）是榴弹飞行方向，用于把撞进方块的起点退回空处。
     * 返回新烟团 id；未生成时返回 -1。
     */
    public static int spawn(ServerWorld world, Vec3d impact, @Nullable Vec3d incomingDirection, @Nullable UUID owner) {
        return -1;
    }

    /**
     * Punches a temporary hole of {@code radius} blocks into every cloud near {@code center}.
     * 在 {@code center} 附近所有烟团中炸开半径 {@code radius} 格的临时空洞。
     */
    public static void carveHole(ServerWorld world, Vec3d center, float radius) {
    }

    /** Removes every cloud in the world and tells clients. / 清除该世界所有烟团并通知客户端。 */
    public static void clearAll(ServerWorld world) {
    }
}
