package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import java.util.Optional;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoellesRoles throwing axe: nearest-wins device hit, attributed to the thrower (owner decision Q4). The axe pierces
 * players, so blocking means "the axe stops at the device": players before the device on this tick's segment are still
 * hit exactly as before, players behind it are not, the device breaks through
 * {@link SeekerDeviceHits#onThrowingAxeSweep} and the axe is removed instead of flying on. The three hooks run only in
 * NoellesRoles' server-side, not-stuck pierce branch (the only {@code World#getOtherEntities} and
 * {@code Box#raycast} calls in {@code tick}); with no device on the segment the cut stays infinite and every player
 * test returns NoellesRoles' own result, so behaviour is unchanged. Coexists with
 * {@code NoellesThrowingAxeVendettaTargetMixin} and {@code JudgeThrowingAxeAttributionMixin}, which only touch
 * {@code onEntityHit}. Default remap: {@code tick} and every {@code @At} target are vanilla members.
 * NoellesRoles 飞斧：“最近者命中”的设备命中，归属于投掷者（所有者决定 Q4）。飞斧会贯穿玩家，因此“遮挡”即
 * “飞斧止于设备”：本刻路径上设备之前的玩家照常被命中，其后的玩家不会被命中，设备经由
 * {@link SeekerDeviceHits#onThrowingAxeSweep} 被打坏，飞斧随即移除而不再继续飞行。三个钩子只在 NoellesRoles
 * 服务端、未插入方块的贯穿分支中运行（{@code tick} 中唯一的 {@code World#getOtherEntities} 与 {@code Box#raycast}
 * 调用）；路径上没有设备时截断距离保持无穷大，每次玩家检测都返回 NoellesRoles 自身的结果，行为不变。
 * 与只作用于 {@code onEntityHit} 的 {@code NoellesThrowingAxeVendettaTargetMixin} 和
 * {@code JudgeThrowingAxeAttributionMixin} 共存。默认 remap：{@code tick} 与所有 {@code @At} 目标均为原版成员。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class SeekerThrowingAxeMixin {
    /**
     * Squared distance from this tick's start to the absorbing device; infinite when none. Server-only, per tick.
     * 本刻起点到吸收命中的设备的平方距离；没有时为无穷大。仅服务端、逐刻重置。
     */
    @Unique
    private double sparkwitch$deviceCutSquared = Double.POSITIVE_INFINITY;

    /**
     * Before the pierce loop: resolve and break a Seeker device on this tick's segment.
     * 贯穿循环之前：判定并打坏本刻路径上的搜寻者设备。
     */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private void sparkwitch$sweepSeekerDevices(CallbackInfo ci) {
        ThrowingAxeEntity axe = (ThrowingAxeEntity) (Object) this;
        Vec3d from = axe.getPos();
        Vec3d to = from.add(axe.getVelocity());
        // Capture the candidate first: a broken device is discarded, but its last box still marks the cut point.
        // 先记录候选设备：设备被打坏后会被移除，但其最后的碰撞箱仍可标出截断点。
        SeekerDeviceEntity candidate = SeekerDeviceRaycast.projectileSweep(axe, from, to);
        sparkwitch$deviceCutSquared = SeekerDeviceHits.onThrowingAxeSweep(axe, axe.getOwner(), from, to)
                ? from.squaredDistanceTo(sparkwitch$impact(candidate, from, to))
                : Double.POSITIVE_INFINITY;
    }

    /**
     * Inside the pierce loop: a player whose entry point is not nearer than the device is shielded by it.
     * 贯穿循环内：入射点不比设备更近的玩家被设备挡住。
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;raycast(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Ljava/util/Optional;"))
    private Optional<Vec3d> sparkwitch$shieldPlayersBehindDevice(Box box, Vec3d from, Vec3d to,
                                                                Operation<Optional<Vec3d>> original) {
        Optional<Vec3d> hit = original.call(box, from, to);
        double cut = sparkwitch$deviceCutSquared;
        return hit.filter(point -> !SeekerDeviceRaycast.deviceWins(cut, from.squaredDistanceTo(point)));
    }

    /**
     * After the pierce loop, before vanilla movement: an axe that hit a device stops there.
     * 贯穿循环之后、原版移动之前：命中设备的飞斧就此停下。
     */
    @Inject(method = "tick", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;tick()V"))
    private void sparkwitch$stopAtSeekerDevice(CallbackInfo ci) {
        if (sparkwitch$deviceCutSquared == Double.POSITIVE_INFINITY) {
            return;
        }
        sparkwitch$deviceCutSquared = Double.POSITIVE_INFINITY;
        ((ThrowingAxeEntity) (Object) this).discard();
        ci.cancel();
    }

    /** Segment entry point into the device box; the segment start when unknown. / 线段进入设备箱体的点；未知时取线段起点。 */
    @Unique
    private static Vec3d sparkwitch$impact(@Nullable SeekerDeviceEntity device, Vec3d from, Vec3d to) {
        if (device == null) {
            return from;
        }
        return device.getBoundingBox().expand(device.targetingMargin()).raycast(from, to).orElse(from);
    }
}
