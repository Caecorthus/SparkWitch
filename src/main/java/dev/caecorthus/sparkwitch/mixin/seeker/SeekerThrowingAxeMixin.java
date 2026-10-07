package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
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
 * {@code onEntityHit}. The pre-loop hook also pierces Magician puppets before the cut
 * ({@code MagicianPuppetHits.onThrowingAxeSweep}), since NoellesRoles' loop sees only players. Default remap:
 * {@code tick} and every {@code @At} target are vanilla members.
 * NoellesRoles 飞斧：“最近者命中”的设备命中，归属于投掷者（所有者决定 Q4）。飞斧会贯穿玩家，因此“遮挡”即
 * “飞斧止于设备”：本刻路径上设备之前的玩家照常被命中，其后的玩家不会被命中，设备经由
 * {@link SeekerDeviceHits#onThrowingAxeSweep} 被打坏，飞斧随即移除而不再继续飞行。三个钩子只在 NoellesRoles
 * 服务端、未插入方块的贯穿分支中运行（{@code tick} 中唯一的 {@code World#getOtherEntities} 与 {@code Box#raycast}
 * 调用）；路径上没有设备时截断距离保持无穷大，每次玩家检测都返回 NoellesRoles 自身的结果，行为不变。
 * 与只作用于 {@code onEntityHit} 的 {@code NoellesThrowingAxeVendettaTargetMixin} 和
 * {@code JudgeThrowingAxeAttributionMixin} 共存。由于 NoellesRoles 的循环只认玩家，循环前的钩子同时贯穿截断点之前的魔术师
 * 皮套（{@code MagicianPuppetHits.onThrowingAxeSweep}）。默认 remap：{@code tick} 与所有 {@code @At} 目标均为原版成员。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class SeekerThrowingAxeMixin {
    /**
     * Search padding around the segment; covers every device box the segment can touch (margins stay below 0.2).
     * 线段搜索外扩量；覆盖线段可能触及的所有设备箱体（瞄准余量均小于 0.2）。
     */
    @Unique
    private static final double SPARKWITCH$SEARCH_PADDING = 1.0D;

    /**
     * Squared distance from this tick's start to the absorbing device; infinite when none. Server-only, per tick.
     * 本刻起点到吸收命中的设备的平方距离；没有时为无穷大。仅服务端、逐刻重置。
     */
    @Unique
    private double sparkwitch$deviceCutSquared = Double.POSITIVE_INFINITY;

    /**
     * Before the pierce loop: resolve and break a Seeker device on this tick's segment. The live devices around the
     * segment are captured first; {@code breakDevice} discards exactly the device the entry chose, so the removed one
     * marks the cut even when a nearer device on the segment was transparent to this thrower (their own, or one they
     * may not break). No device near the segment: the entry cannot hit anything and is skipped.
     * 贯穿循环之前：判定并打坏本刻路径上的搜寻者设备。先记录线段附近的存活设备；{@code breakDevice} 只会移除入口选中的
     * 那台设备，因此即使线段上更近的设备对该投掷者透明（其自己的设备或无权打坏的设备），被移除的那台仍能准确标出截断点。
     * 线段附近没有设备时入口不可能命中，直接跳过。
     */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private void sparkwitch$sweepSeekerDevices(CallbackInfo ci) {
        ThrowingAxeEntity axe = (ThrowingAxeEntity) (Object) this;
        Vec3d from = axe.getPos();
        Vec3d to = from.add(axe.getVelocity());
        List<SeekerDeviceEntity> nearby = axe.getWorld().getEntitiesByClass(SeekerDeviceEntity.class,
                new Box(from, to).expand(SPARKWITCH$SEARCH_PADDING), device -> device.isAlive() && !device.isRemoved());
        sparkwitch$deviceCutSquared = !nearby.isEmpty()
                && SeekerDeviceHits.onThrowingAxeSweep(axe, axe.getOwner(), from, to)
                ? sparkwitch$cutSquared(nearby, from, to)
                : Double.POSITIVE_INFINITY;
        // Magician seam: puppets on this segment before the cut are pierced like players; the axe flies on.
        // 魔术师接缝：本刻线段上截断点之前的皮套与玩家一样被贯穿；飞斧继续飞行。
        MagicianPuppetHits.onThrowingAxeSweep(axe, axe.getOwner(), from, to, sparkwitch$deviceCutSquared);
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

    /**
     * Squared distance from the segment start to where it enters the device the entry broke (the one now removed);
     * falls back to the nearest device on the segment, then to the start (the device absorbed everything this tick).
     * 线段起点到其进入被入口打坏（即已被移除）的设备处的平方距离；找不到时退回线段上最近的设备，再退回起点
     * （本刻一切都被设备挡下）。
     */
    @Unique
    private static double sparkwitch$cutSquared(List<SeekerDeviceEntity> nearby, Vec3d from, Vec3d to) {
        double broken = Double.POSITIVE_INFINITY;
        double any = Double.POSITIVE_INFINITY;
        for (SeekerDeviceEntity device : nearby) {
            double distance = sparkwitch$entrySquared(device, from, to);
            if (distance < 0.0) {
                continue;
            }
            any = Math.min(any, distance);
            if (device.isRemoved()) {
                broken = Math.min(broken, distance);
            }
        }
        if (broken != Double.POSITIVE_INFINITY) {
            return broken;
        }
        return any != Double.POSITIVE_INFINITY ? any : 0.0D;
    }

    /** Segment entry into the margin-grown device box; 0 inside it, -1 on a miss. / 线段进入设备扩展箱体的平方距离。 */
    @Unique
    private static double sparkwitch$entrySquared(SeekerDeviceEntity device, Vec3d from, Vec3d to) {
        Box box = device.getBoundingBox().expand(Math.max(0.0D, device.targetingMargin()));
        if (box.contains(from)) {
            return 0.0D;
        }
        return box.raycast(from, to).map(from::squaredDistanceTo).orElse(-1.0D);
    }
}
