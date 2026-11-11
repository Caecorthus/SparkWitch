package dev.caecorthus.sparkwitch.mixin.hanna;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaFloatService;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A projectile that hits a floating player interrupts the float ({@code HannaRules}: "a projectile hit from another
 * living player"). Read-only seam: it never cancels and never changes the hit; {@link HannaFloatService#touch} decides
 * whether the thrower counts and does nothing for a player who is not floating.
 * <p>Why here: {@code hitOrDeflect} is where every vanilla projectile tick ({@code ThrownEntity},
 * {@code PersistentProjectileEntity}, ...) hands over its collision, and its single {@code onCollision} call is
 * reached only for a hit the target did not deflect. Injecting before that call, in the base class, does not depend on
 * what a subclass does afterwards: BEAR's smoke round overrides {@code onCollision} without calling {@code super}, and
 * NoellesRoles' Throwing Axe and the ninja shuriken override {@code onEntityHit} (the former seam) without calling
 * {@code super}.
 * <p>Server only: on a client the target is never a {@link ServerPlayerEntity}. A hit a projectile resolves on its own,
 * outside {@code hitOrDeflect} (the Throwing Axe's sweep, the lag-compensated potion shell and wither skull), is not
 * seen here; when it is a kill attempt, {@code KillPlayer.BEFORE} interrupts the float instead.
 * 命中浮游玩家的投掷物会打断浮游（{@code HannaRules}：「其他存活玩家的投掷物命中」）。只读接缝：从不取消、从不改变命中；由
 * {@link HannaFloatService#touch} 判定投掷者是否算数，对未在浮游的玩家什么都不做。
 * <p>为何在此：{@code hitOrDeflect} 是所有原版投掷物 tick（{@code ThrownEntity}、{@code PersistentProjectileEntity} 等）移交
 * 碰撞的地方，其中唯一的 {@code onCollision} 调用只在目标没有偏转该命中时才会到达。在基类中、该调用之前注入，不依赖子类之后的
 * 行为：BEAR 的烟雾榴弹重写 {@code onCollision} 且不调用 {@code super}，NoellesRoles 的投掷斧与忍者手里剑重写
 * {@code onEntityHit}（原先的接缝）且不调用 {@code super}。
 * <p>仅服务端：客户端上的目标永远不是 {@link ServerPlayerEntity}。投掷物在 {@code hitOrDeflect} 之外自行结算的命中（投掷斧的
 * 扫掠、延迟补偿的药剂炮弹与凋零头）不会经过这里；若它是击杀尝试，则由 {@code KillPlayer.BEFORE} 打断浮游。
 */
@Mixin(ProjectileEntity.class)
public abstract class HannaProjectileHitMixin {
    @Inject(
            method = "hitOrDeflect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileEntity;onCollision(Lnet/minecraft/util/hit/HitResult;)V")
    )
    private void sparkwitch$touchFloatingHannaOnProjectileHit(HitResult hitResult,
                                                              CallbackInfoReturnable<ProjectileDeflection> cir) {
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayerEntity target) {
            HannaFloatService.touch(target, ((ProjectileEntity) (Object) this).getOwner());
        }
    }
}
