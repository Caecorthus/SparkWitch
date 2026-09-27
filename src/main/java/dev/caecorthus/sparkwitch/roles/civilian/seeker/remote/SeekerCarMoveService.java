package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Frozen contract: applies validated car moves, sends corrections and calls {@code SeekerCarEntity#onDrivenMove} for
 * every accepted move.
 * TODO(WP-09): implement. / 待 WP-09 实现。
 * 冻结契约：执行已校验的小车移动、发送纠正，并对每次被接受的移动调用 {@code SeekerCarEntity#onDrivenMove}。
 */
public final class SeekerCarMoveService {
    private SeekerCarMoveService() {
    }

    /** Stale or mismatched packets are dropped silently. / 过期或不匹配的包静默丢弃。 */
    public static void handleMove(ServerPlayerEntity player, SeekerCarMoveC2SPacket packet) {
    }
}
