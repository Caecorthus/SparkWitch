package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.gate.RiftGateCrosshairAccess;
import dev.caecorthus.sparkwitch.client.riftwalker.gate.RiftGateCrosshairClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Melee pass-through, crosshair half (R1): the Rift Gate keeps {@code canHit()} only so its users can right-click it,
 * so the vanilla crosshair predicate drops gates for everyone else (non-users never aim at a gate, a player behind one
 * is hit). It ANDs the predicate inside {@code findCrosshairTarget} (the {@code WraithCrosshairTargetMixin} shape), so
 * vanilla still resolves entity-vs-block and reach, and the Rift/Seeker forced-MISS handlers at
 * {@code updateCrosshairTarget} RETURN still win. Also exposes the gate-free re-pick used by {@code RiftGateAttackMixin}.
 * 近战穿门的准星部分（R1）：裂隙门保留 {@code canHit()} 只为让使用者右键进门，因此原版准星判定对其他所有人去掉门（非使用者
 * 永远瞄不到门，门后的玩家会被击中）。它在 {@code findCrosshairTarget} 内部与原判定取「与」（同 {@code WraithCrosshairTargetMixin}），
 * 原版照常处理实体/方块与距离，裂隙/搜寻者在 {@code updateCrosshairTarget} RETURN 处的强制 MISS 仍然优先。另提供
 * {@code RiftGateAttackMixin} 使用的忽略门重选。
 */
@Mixin(GameRenderer.class)
public abstract class RiftGateCrosshairMixin implements RiftGateCrosshairAccess {
    @Shadow
    @Final
    MinecraftClient client;

    @Shadow
    private HitResult findCrosshairTarget(Entity camera, double blockInteractionRange, double entityInteractionRange,
                                          float tickDelta) {
        throw new AssertionError();
    }

    @ModifyArg(
            method = "findCrosshairTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"
            ),
            index = 4
    )
    private Predicate<Entity> sparkwitch$crosshairThroughRiftGates(
            Entity camera,
            Vec3d start,
            Vec3d end,
            Box box,
            Predicate<Entity> predicate,
            double maxDistanceSquared
    ) {
        return RiftGateCrosshairClient.filterCrosshair(camera, predicate);
    }

    @Override
    @Nullable
    public HitResult sparkwitch$pickIgnoringGates() {
        Entity camera = client.getCameraEntity();
        ClientPlayerEntity player = client.player;
        if (camera == null || player == null || client.world == null) {
            return null;
        }
        // Same arguments as updateCrosshairTarget(1.0F) in the client tick that runs doAttack.
        // 参数与执行 doAttack 的客户端 tick 中 updateCrosshairTarget(1.0F) 相同。
        return RiftGateCrosshairClient.ignoringGates(() -> findCrosshairTarget(camera,
                player.getBlockInteractionRange(), player.getEntityInteractionRange(), 1.0F));
    }
}
