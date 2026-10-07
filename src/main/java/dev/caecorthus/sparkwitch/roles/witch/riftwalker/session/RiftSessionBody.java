package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * Server-only body moves of the alive-spectator model (plan §6.2). Every Riftwalker teleport goes through the 7-arg
 * {@code ServerPlayerEntity#teleport} (it dismounts and wakes, never {@code setCameraEntity}) with the own-teleport
 * marker set, so {@code RiftSessionNetworkHandlerMixin} can tell our anchor moves apart from a foreign server teleport
 * (NR Swapper, Kidnapper, admin): only foreign ones may end a session as BODY_MOVED. Server thread only.
 * 活着的旁观者模型的仅服务端本体移动（plan §6.2）。隙行者的每次传送都经由 7 参数的 {@code ServerPlayerEntity#teleport}
 * （会下坐骑、唤醒，从不调用 {@code setCameraEntity}）并设置「自身传送」标记，使 {@code RiftSessionNetworkHandlerMixin}
 * 能区分我们的锚点移动与外部服务端传送（NR 交换者、绑架者、管理员）：只有外部传送才可能以 BODY_MOVED 结束会话。仅服务端线程。
 */
final class RiftSessionBody {
    /** Re-entrancy-safe marker; server thread only. / 可重入的标记；仅服务端线程。 */
    private static int ownTeleportDepth;

    private RiftSessionBody() {
    }

    static boolean isOwnTeleport() {
        return ownTeleportDepth > 0;
    }

    /**
     * Moves the body; {@code keepLook} sends the rotation as relative zero so the client keeps its own view (anchor
     * holds and exits), otherwise the absolute {@code yaw}/{@code pitch} are applied (entry and hops face out of the
     * gate). Velocity and fall distance are reset.
     * 移动本体；{@code keepLook} 时旋转以相对零发送，客户端保持自己的视角（锚点保持与出门），否则应用绝对的
     * {@code yaw}/{@code pitch}（进门与跳门时面朝门外）。同时重置速度与摔落距离。
     */
    static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d feet, float yaw, float pitch,
                         boolean keepLook) {
        ownTeleportDepth++;
        try {
            if (keepLook) {
                player.teleport(world, feet.x, feet.y, feet.z, PositionFlag.ROT, player.getYaw(), player.getPitch());
            } else {
                player.teleport(world, feet.x, feet.y, feet.z, Set.of(), yaw, pitch);
            }
        } finally {
            ownTeleportDepth--;
        }
        settle(player);
    }

    static void settle(ServerPlayerEntity player) {
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
    }

    /**
     * Vanilla re-evaluates entity tracking only on chunk-section changes or move packets, and {@code changeGameMode}
     * does not; refresh it at once so other players stop (or start) tracking the body and the occupant drops other
     * (invisible) spectators through Wathe's {@code canBeSpectated} rule.
     * 原版只在区块段变化或收到移动包时重新评估实体追踪，{@code changeGameMode} 不会触发；在此立即刷新，使其他玩家停止（或开始）
     * 追踪本体，并让门内玩家经 Wathe 的 {@code canBeSpectated} 规则不再追踪其他（隐身的）旁观者。
     */
    static void refreshTracking(ServerPlayerEntity player, ServerWorld world) {
        world.getChunkManager().updatePosition(player);
    }

    /**
     * Small puff and low teleport sound at the gate, audible to everyone nearby (counterplay cue, plan §5.3); hops are
     * silent and never call this.
     * 门口的一小团粒子与低音量传送声，附近所有人都可见可闻（反制线索，plan §5.3）；跳门静默，从不调用此方法。
     */
    static void cue(ServerWorld world, Vec3d gatePos, boolean entering) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, gatePos.x, gatePos.y + 1.0, gatePos.z,
                24, 0.3, 0.7, 0.3, 0.02);
        world.playSound(null, gatePos.x, gatePos.y + 1.0, gatePos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, RiftSessionRules.CUE_VOLUME, entering ? 1.3F : 0.9F);
    }
}
