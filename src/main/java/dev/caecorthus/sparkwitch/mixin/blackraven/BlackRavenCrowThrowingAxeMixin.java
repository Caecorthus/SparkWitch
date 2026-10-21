package dev.caecorthus.sparkwitch.mixin.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoellesRoles throwing axe on a Raven Form crow (plan v2 §2.5). The axe pierces players: before vanilla movement its
 * server tick walks {@code getOtherEntities} and hits every player whose bare box the tick's segment enters. At that
 * same call, this hands the substituted owner of every crow on the segment ({@link BlackRavenCrowHits#onThrowingAxeSweep}:
 * current box, no block clip, spared behind a breakable Seeker device) to the axe's own {@code onEntityHit}, so
 * NoellesRoles' player branch (Vendetta redirect, Judge attribution, the kill, the trident sound and the 0.9 slow-down)
 * runs on the Black Raven, and the axe flies on. Priority 900 runs this before the Seeker device sweep at the same
 * call, so the device that cuts the segment is still alive when measured. Extends the projectile so the virtual call
 * reaches NoellesRoles' override.
 * 化鸦乌鸦上的 NoellesRoles 飞斧（计划 v2 §2.5）。飞斧贯穿玩家：其服务端 tick 在原版移动前遍历 {@code getOtherEntities}，
 * 命中本刻线段进入其裸箱体的每名玩家。在同一调用处，本 mixin 把线段上每只乌鸦被替身的主人（{@link BlackRavenCrowHits#onThrowingAxeSweep}：
 * 当前箱体、不按方块截断、位于可打坏的搜寻者设备之后则幸免）交给飞斧自身的 {@code onEntityHit}，因此 NoellesRoles 的玩家分支
 * （复仇者重定向、审判官归因、击杀、三叉戟声与 0.9 减速）作用于黑羽鸦，飞斧继续飞行。优先级 900 使本注入先于同一调用处的
 * 搜寻者设备扫掠执行，因此截断线段的设备在量取时仍然存在。继承投射物类，使虚调用到达 NoellesRoles 的覆写。
 */
@Mixin(value = ThrowingAxeEntity.class, priority = 900)
public abstract class BlackRavenCrowThrowingAxeMixin extends PersistentProjectileEntity {
    protected BlackRavenCrowThrowingAxeMixin(EntityType<? extends PersistentProjectileEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"),
            require = 1)
    private void sparkwitch$pierceCrows(CallbackInfo ci) {
        if (getWorld().isClient()) {
            return;
        }
        Vec3d from = getPos();
        Vec3d to = from.add(getVelocity());
        for (ServerPlayerEntity owner : BlackRavenCrowHits.onThrowingAxeSweep(this, getOwner(), from, to)) {
            onEntityHit(new EntityHitResult(owner));
        }
    }
}
