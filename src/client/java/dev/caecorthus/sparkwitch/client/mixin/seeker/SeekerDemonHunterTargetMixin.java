package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.client.demonhunter.DemonHunterClientHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client Demon Hunter pistol target (NoellesRoles client helper): nearest-wins device targeting (owner decision Q4).
 * NoellesRoles' own {@code getGunTarget} only sees alive survival players, so its result is kept unless a Seeker device
 * lies nearer on the 5-block look ray; then the device's {@code EntityHitResult} is sent instead. NoellesRoles'
 * {@code resolveTargetFromHitResult} already forwards any entity id, and the server receiver
 * ({@code SeekerDemonHunterHitMixin}) alone decides whether the device breaks. Client-only prediction: nothing here is
 * trusted by the server. The class is {@code remap = false} (NoellesRoles members); the selectors carry no vanilla
 * member names.
 * 客户端猎魔枪目标（NoellesRoles 客户端辅助类）：“最近者命中”的设备瞄准（所有者决定 Q4）。
 * NoellesRoles 自身的 {@code getGunTarget} 只识别存活的生存模式玩家，因此除非 5 格视线上有更近的搜寻者设备，
 * 否则保留其结果；有则改为发送该设备的 {@code EntityHitResult}。NoellesRoles 的
 * {@code resolveTargetFromHitResult} 本就会转发任意实体 id，是否打坏设备只由服务端接收器
 * （{@code SeekerDemonHunterHitMixin}）决定。仅为客户端预测，服务端不信任这里的任何结果。
 * 本类为 {@code remap = false}（NoellesRoles 成员），选择器中不含原版成员名。
 */
@Mixin(value = DemonHunterClientHelper.class, remap = false)
public abstract class SeekerDemonHunterTargetMixin {
    /** Mirrors NoellesRoles' {@code getGunTarget} reach (5 blocks). / 与 NoellesRoles {@code getGunTarget} 的 5 格射程一致。 */
    @Unique
    private static final double SPARKWITCH$PISTOL_RANGE = 5.0D;

    @WrapOperation(method = "handleClientShoot", at = @At(value = "INVOKE",
            target = "Lorg/agmas/noellesroles/demonhunter/DemonHunterPistolItem;getGunTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;"))
    private static HitResult sparkwitch$preferNearerSeekerDevice(PlayerEntity user, Operation<HitResult> original) {
        return SeekerDeviceRaycast.preferNearerDevice(user, original.call(user), SPARKWITCH$PISTOL_RANGE);
    }
}
