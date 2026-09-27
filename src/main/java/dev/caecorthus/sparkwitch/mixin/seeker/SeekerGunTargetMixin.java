package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.doctor4t.wathe.item.DerringerItem;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client gun target: wraps getGunTarget in RevolverItem/DerringerItem#use (vanilla override, remap = true) for
 * nearest-wins device targeting (owner decision Q4, BLOCKING rule). The original is called first, so SparkTraits
 * Marksman's HEAD replacement (extended range) is kept; then a Seeker device strictly nearer than the original hit (or
 * anywhere on the ray when nothing was hit) replaces it, so Wathe sends that device's id and the server
 * ({@code SeekerGunDeviceHitMixin}) validates and breaks it while the player behind is not shot. {@code use} only
 * calls getGunTarget on the client; the static Wathe members are {@code remap = false} on their {@code @At}.
 * 客户端枪械目标：在 RevolverItem/DerringerItem#use（原版覆写，remap = true）中包装 getGunTarget，实现“最近者命中”的
 * 设备瞄准（所有者决定 Q4 的遮挡规则）。先调用原方法以保留 SparkTraits 神射手在 HEAD 的替换（延长射程）；随后若有严格
 * 比原命中更近（未命中时为射线上任意处）的搜寻者设备则替换结果，Wathe 便发送该设备 id，由服务端
 * （{@code SeekerGunDeviceHitMixin}）校验并打坏，身后的玩家不会中枪。{@code use} 只在客户端调用 getGunTarget；
 * Wathe 的静态成员在其 {@code @At} 上使用 {@code remap = false}。
 */
@Mixin({RevolverItem.class, DerringerItem.class})
public abstract class SeekerGunTargetMixin {
    /** Wathe's native ranges; only a fallback, the original hit distance is used first. / Wathe 原生射程，仅作兜底。 */
    @Unique
    private static final double SPARKWITCH$REVOLVER_RANGE = 30.0;
    @Unique
    private static final double SPARKWITCH$DERRINGER_RANGE = 7.0;

    @WrapOperation(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
            at = {
                    @At(value = "INVOKE",
                            target = "Ldev/doctor4t/wathe/item/RevolverItem;getGunTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;",
                            remap = false),
                    @At(value = "INVOKE",
                            target = "Ldev/doctor4t/wathe/item/DerringerItem;getGunTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;",
                            remap = false)
            })
    private HitResult sparkwitch$preferNearerSeekerDevice(PlayerEntity user, Operation<HitResult> original) {
        HitResult result = original.call(user);
        double range = (Object) this instanceof DerringerItem ? SPARKWITCH$DERRINGER_RANGE : SPARKWITCH$REVOLVER_RANGE;
        return SeekerDeviceRaycast.preferNearerDevice(user, result, range);
    }
}
