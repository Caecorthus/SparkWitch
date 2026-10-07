package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手雷爆炸前让范围内的魔术师皮套进入“被手雷击杀/强制结束”流程。
 * Wathe 原版爆炸只遍历 ServerPlayerEntity，皮套是自定义 LivingEntity，必须单独桥接。
 * Same blast rule as the Seeker devices: the 3-block sphere around the grenade box centre with a clear line of sight,
 * never the thrower's own puppet; players are still killed by Wathe's own loop afterwards (no cancel).
 * 与搜寻者设备相同的爆炸规则：以手雷碰撞箱中心为球心、半径 3 格且视线畅通，从不结束投掷者自己的皮套；
 * 玩家随后仍由 Wathe 自己的循环击杀（不取消）。
 */
@Mixin(GrenadeEntity.class)
public abstract class MagicianGrenadePlaybackHitMixin extends ThrownItemEntity {
    protected MagicianGrenadePlaybackHitMixin(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void sparkwitch$stopPlaybackInExplosion(HitResult hitResult, CallbackInfo ci) {
        if (isRemoved() || !(getWorld() instanceof ServerWorld world)) return;
        MagicianPuppetHits.onBlast(
                world,
                getBoundingBox().getCenter(),
                SeekerDamageRules.GRENADE_RADIUS,
                getOwner() instanceof ServerPlayerEntity player ? player : null,
                GameConstants.DeathReasons.GRENADE,
                getStack().getTranslationKey()
        );
    }
}
