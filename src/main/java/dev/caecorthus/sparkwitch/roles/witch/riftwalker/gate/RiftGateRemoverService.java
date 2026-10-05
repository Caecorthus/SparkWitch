package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the {@link RiftGateRemoverItem}: an operator (permission node
 * {@link SparkWitchPermissions#ITEM_RIFT_GATE_REMOVER}, op level 2 without a permissions mod) deletes the Rift Gate
 * under the crosshair. The server re-picks the target by its own ray (blocks in the way stop it), so the client's
 * crosshair is never trusted. A listed gate closes through {@link RiftGateRegistry#close} with
 * {@link RiftGateCloseReason#ADMIN}, which force-exits its occupants like any other close; a stray unlisted entity is
 * just discarded. Works in any game state and for any role. Server thread only.
 * {@link RiftGateRemoverItem} 的服务端：管理员（权限节点 {@link SparkWitchPermissions#ITEM_RIFT_GATE_REMOVER}，未装权限模组时
 * 为 op 2 级）删除准星所指的裂隙门。服务端用自己的射线重新选取目标（中间有方块则挡住），从不信任客户端准星。已登记的门经
 * {@link RiftGateRegistry#close} 以 {@link RiftGateCloseReason#ADMIN} 关闭，门内的人与其他关闭方式一样被强制出门；未登记的
 * 残留实体直接移除。任何对局状态、任何职业都可使用。仅服务端线程。
 */
public final class RiftGateRemoverService {
    /** How far the remover reaches, in blocks (an admin convenience, longer than hand reach). / 清除工具的距离（格）。 */
    public static final double REACH = 24.0;
    static final String REMOVED_KEY = "message.sparkwitch.rift_gate_remover.removed";
    static final String NO_TARGET_KEY = "message.sparkwitch.rift_gate_remover.no_target";
    static final String NO_PERMISSION_KEY = "message.sparkwitch.rift_gate_remover.no_permission";
    /** Removal cue at the gate: a reverse-portal puff and a power-down sound. / 门处的删除提示：反向传送门粒子与断能音效。 */
    static final double CUE_LIFT = 1.0;
    static final int CUE_PARTICLES = 28;
    static final float CUE_VOLUME = 0.7F;
    static final float CUE_PITCH = 1.3F;

    private RiftGateRemoverService() {
    }

    /** Returns SUCCESS (the server swings the hand) only when a gate was removed. / 只有删除了门才返回 SUCCESS（服务端挥手）。 */
    public static ActionResult tryRemove(ServerPlayerEntity player) {
        if (!Permissions.check(player, SparkWitchPermissions.ITEM_RIFT_GATE_REMOVER,
                SparkWitchPermissions.DEFAULT_COMMAND_LEVEL)) {
            player.sendMessage(Text.translatable(NO_PERMISSION_KEY), true);
            return ActionResult.FAIL;
        }
        RiftGateEntity gate = aimedGate(player);
        if (gate == null) {
            player.sendMessage(Text.translatable(NO_TARGET_KEY), true);
            return ActionResult.FAIL;
        }
        ServerWorld world = player.getServerWorld();
        int number = gate.gateNumber();
        Vec3d pos = gate.getPos();
        if (!RiftGateRegistry.isListed(world, gate) || !RiftGateRegistry.close(world, number, RiftGateCloseReason.ADMIN)) {
            gate.discard();
        }
        playCue(world, pos);
        SparkWitch.LOGGER.info("{} removed Rift Gate #{} at {} with the Rift Gate Remover",
                player.getName().getString(), number, pos);
        player.sendMessage(Text.translatable(REMOVED_KEY, number), true);
        return ActionResult.SUCCESS;
    }

    /**
     * The first live gate along the look ray within {@link #REACH}, cut short by the first block outline (what the
     * player can see and target), like the vanilla crosshair.
     * 视线射线上 {@link #REACH} 格内的第一扇存活的门；射线在第一个方块轮廓处截止（玩家能看到、能选中的），与原版准星一致。
     */
    @Nullable
    private static RiftGateEntity aimedGate(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(REACH));
        BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        Box search = player.getBoundingBox().stretch(end.subtract(eye)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, search,
                RiftGateRemoverService::isLiveGate, eye.squaredDistanceTo(end));
        return hit != null && hit.getEntity() instanceof RiftGateEntity gate ? gate : null;
    }

    private static boolean isLiveGate(Entity entity) {
        return entity instanceof RiftGateEntity && !entity.isRemoved();
    }

    private static void playCue(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + CUE_LIFT, pos.z, CUE_PARTICLES,
                0.35, 0.8, 0.35, 0.05);
        world.playSound(null, pos.x, pos.y + CUE_LIFT, pos.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                SoundCategory.BLOCKS, CUE_VOLUME, CUE_PITCH);
    }
}
