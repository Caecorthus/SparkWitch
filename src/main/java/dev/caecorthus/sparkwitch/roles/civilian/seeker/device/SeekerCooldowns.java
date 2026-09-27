package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCooldownReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Frozen contract: the single "max + exact" writer of the {@code seeker_car} item cooldown. A longer existing cooldown
 * is kept; SparkTraits' exact write is used first and the vanilla set only when it returns false (never both).
 * TODO(WP-03): implement. / 待 WP-03 实现。
 * 冻结契约：{@code seeker_car} 物品冷却“取大 + 精确”的唯一写入方。保留更长的既有冷却；
 * 优先使用 SparkTraits 精确写入，仅当其返回 false 时才回退原版写入（绝不写两次）。
 */
public final class SeekerCooldowns {
    private SeekerCooldowns() {
    }

    /** @return true when a new value was written. / 写入了新值时返回 true。 */
    public static boolean writeFloorExact(ServerPlayerEntity player, int ticks, SeekerCooldownReason reason) {
        return false;
    }

    /**
     * Remaining ticks on the car item cooldown; 0 when none. Side-neutral: the client reads its synced vanilla cooldown
     * (HUD, console), the server its authoritative one.
     * 小车物品冷却的剩余刻数；无冷却时为 0。两端通用：客户端读取已同步的原版冷却（HUD、控制台），服务端读取权威冷却。
     */
    public static int remainingTicks(PlayerEntity player) {
        return 0;
    }
}
