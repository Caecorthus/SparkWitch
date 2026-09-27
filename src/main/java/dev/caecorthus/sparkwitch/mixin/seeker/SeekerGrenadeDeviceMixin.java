package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe grenade (incl. Bomb Maniac): onCollision HEAD blast (sphere + line of sight) breaks devices; server only.
 * The blast is an area effect, not a ray: devices inside a 3-block sphere around the grenade box centre with a clear
 * COLLIDER segment to any sample point break, attributed to the thrower when it is an online player; players are
 * still killed by Wathe's own loop afterwards, unchanged (no cancel, no redirect). {@code onCollision} is a vanilla
 * override, so the selector keeps {@code remap = true}.
 * Wathe 手雷（含炸弹狂人）：onCollision HEAD 处的爆炸（球 + 视线）打坏设备；仅服务端。爆炸是范围效果而非射线：
 * 以手雷碰撞箱中心为球心、半径 3 格内且到任一采样点有畅通 COLLIDER 线段的设备都会被打坏，投掷者为在线玩家时归属于他；
 * 玩家随后仍由 Wathe 自己的循环照常击杀（不取消、不重定向）。{@code onCollision} 是原版覆写，选择器保持 {@code remap = true}。
 */
@Mixin(GrenadeEntity.class)
public abstract class SeekerGrenadeDeviceMixin {
    @Inject(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V", at = @At("HEAD"))
    private void sparkwitch$breakSeekerDevicesInBlast(HitResult hitResult, CallbackInfo ci) {
        GrenadeEntity grenade = (GrenadeEntity) (Object) this;
        if (grenade.isRemoved() || !(grenade.getWorld() instanceof ServerWorld world)) {
            return;
        }
        ServerPlayerEntity thrower = grenade.getOwner() instanceof ServerPlayerEntity player ? player : null;
        SeekerDeviceHits.onBlast(world, grenade.getBoundingBox().getCenter(), SeekerDamageRules.GRENADE_RADIUS,
                thrower, SeekerBreakSource.GRENADE);
    }
}
