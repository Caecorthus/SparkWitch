package dev.caecorthus.sparkwitch.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile.RiftGateProjectileService;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * NoellesRoles throwing axe through Rift Gates. The axe's entity lookup returns null and it kills players in its own
 * server pierce loop before {@code super.tick}, so the vanilla deflection seam never sees a gate. Nearest-wins, like
 * {@code SeekerThrowingAxeMixin} (stacked on the same three anchors, inject/wrap only): before the loop the nearest gate
 * the segment enters before any block is resolved; players nearer than the gate are still hit exactly as before, players
 * behind it are shielded; after the loop the axe is teleported to another gate's front (or reflected) and ends this
 * tick there, so vanilla movement is skipped once and the next tick's pierce loop starts at the exit. With no gate on the
 * segment the cut stays infinite and NoellesRoles' behaviour is unchanged. When a Seeker device stops the axe first,
 * Seeker's cancel wins (the accepted plan §9 edge): priority 1100 applies this mixin after the default-1000
 * {@code SeekerThrowingAxeMixin}, so at each shared anchor Seeker's callback runs first (the device is broken before
 * the gate lookup, and Seeker's cancel stops the tick before this pass), whatever the mixin-JSON order. Default remap:
 * {@code tick} and every {@code @At} target are vanilla members.
 * NoellesRoles 飞斧穿越裂隙门。飞斧的实体查找返回 null，并在 {@code super.tick} 之前用自己的服务端贯穿循环击杀玩家，
 * 原版偏转接缝因此看不到门。与 {@code SeekerThrowingAxeMixin} 一样遵循“最近者命中”（叠加在相同的三个锚点上，只用
 * inject/wrap）：循环前解析线段在碰到方块之前进入的最近门；比门更近的玩家照常被命中，门后的玩家被挡住；循环后飞斧被传送到
 * 另一扇门正面（或反弹），本刻即停在那里，因此跳过一次原版移动，下一刻的贯穿循环从出口开始。线段上没有门时截断距离保持
 * 无穷大，NoellesRoles 行为不变。若搜寻者设备先拦下飞斧，以搜寻者的取消为准（plan §9 已接受的边角情况）：优先级 1100 使本
 * mixin 在默认 1000 的 {@code SeekerThrowingAxeMixin} 之后应用，因此在每个共享锚点上搜寻者的回调先执行（先打坏设备再查找门，
 * 搜寻者的取消会在本次穿门前结束本刻），与 mixin JSON 的顺序无关。默认 remap：{@code tick} 与所有 {@code @At} 目标均为原版成员。
 */
@Mixin(value = ThrowingAxeEntity.class, priority = 1100)
public abstract class RiftThrowingAxeMixin {
    /** Gate the axe meets this tick; server-only, cleared every tick. / 飞斧本刻遇到的门；仅服务端，逐刻清空。 */
    @Unique
    @Nullable
    private RiftGateEntity sparkwitch$riftGate;

    /** Squared distance from this tick's start to that gate; infinite when none. / 本刻起点到该门的平方距离；没有时为无穷大。 */
    @Unique
    private double sparkwitch$riftGateCutSquared = Double.POSITIVE_INFINITY;

    /** Before the pierce loop: find the nearest gate on this tick's segment. / 贯穿循环之前：查找本刻线段上最近的门。 */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private void sparkwitch$findRiftGate(CallbackInfo ci) {
        ThrowingAxeEntity axe = (ThrowingAxeEntity) (Object) this;
        Vec3d from = axe.getPos();
        RiftGateProjectileService.ThrowingAxeGateHit hit =
                RiftGateProjectileService.findThrowingAxeGate(axe, from, from.add(axe.getVelocity()));
        sparkwitch$riftGate = hit == null ? null : hit.gate();
        sparkwitch$riftGateCutSquared = hit == null ? Double.POSITIVE_INFINITY : hit.entrySquared();
    }

    /** Inside the pierce loop: players not nearer than the gate are shielded by it. / 贯穿循环内：不比门更近的玩家被门挡住。 */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;raycast(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Ljava/util/Optional;"))
    private Optional<Vec3d> sparkwitch$shieldPlayersBehindRiftGate(Box box, Vec3d from, Vec3d to,
                                                                  Operation<Optional<Vec3d>> original) {
        Optional<Vec3d> hit = original.call(box, from, to);
        double cut = sparkwitch$riftGateCutSquared;
        if (cut == Double.POSITIVE_INFINITY) {
            return hit;
        }
        return hit.filter(point -> from.squaredDistanceTo(point) < cut);
    }

    /** After the pierce loop, before vanilla movement: pass the gate and end the tick there. / 贯穿循环之后、原版移动之前：穿门并就此结束本刻。 */
    @Inject(method = "tick", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;tick()V"))
    private void sparkwitch$passRiftGate(CallbackInfo ci) {
        RiftGateEntity gate = sparkwitch$riftGate;
        if (gate == null) {
            return;
        }
        sparkwitch$riftGate = null;
        sparkwitch$riftGateCutSquared = Double.POSITIVE_INFINITY;
        if (RiftGateProjectileService.passThrowingAxe((ThrowingAxeEntity) (Object) this, gate)) {
            ci.cancel();
        }
    }
}
