package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client knife-stab target: nearest-wins device targeting for every Wathe knife stab. Hooks the RETURN of Wathe's
 * static producer {@code KnifeItem.getKnifeTarget(PlayerEntity)} (a Wathe member, so the selector is remap = false),
 * so every caller sees the same pick: the charged release in {@code KnifeItem#onStoppedUsing}, the instant stabs that
 * call it directly from their own {@code KnifeItem#use} HEAD handlers (NoellesRoles Scavenger, SparkStrength Veteran /
 * Coroner disguise), and Wathe's crosshair knife hint, which now lights up on a nearer device too. A Seeker device
 * strictly nearer than the original hit replaces it, so the caller sends a {@code KnifeStabPayload} with the device id
 * and {@code SeekerKnifeStabDeviceMixin} validates and breaks it on the server; the player behind is not stabbed.
 * Client only: a server-side call returns the original untouched.
 * 客户端刀刺目标：所有 Wathe 刀刺的“最近者命中”设备瞄准。挂在 Wathe 静态方法
 * {@code KnifeItem.getKnifeTarget(PlayerEntity)} 的 RETURN 处（Wathe 成员，选择器为 remap = false），
 * 因此所有调用方得到同一结果：{@code KnifeItem#onStoppedUsing} 的蓄力释放、在各自 {@code KnifeItem#use} HEAD 中直接调用它的
 * 瞬刺（NoellesRoles 拾荒者、SparkStrength 老兵/验尸官伪装），以及 Wathe 准星的刀提示（现在对更近的设备也会亮起）。
 * 若有严格比原命中更近的搜寻者设备则替换结果，调用方便以设备 id 发送 {@code KnifeStabPayload}，由服务端
 * {@code SeekerKnifeStabDeviceMixin} 校验并打坏；身后的玩家不会被刺中。仅客户端：服务端调用原样返回。
 */
@Mixin(KnifeItem.class)
public abstract class SeekerKnifeTargetMixin {
    /** Wathe's knife ray length; only a fallback. / Wathe 刀的射线长度，仅作兜底。 */
    @Unique
    private static final double SPARKWITCH$KNIFE_RANGE = 3.0;

    @ModifyReturnValue(
            method = "getKnifeTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;",
            at = @At("RETURN"),
            remap = false)
    private static HitResult sparkwitch$preferNearerSeekerDevice(HitResult original, PlayerEntity user) {
        if (user == null || !user.getWorld().isClient()) {
            return original;
        }
        return SeekerDeviceRaycast.preferNearerDevice(user, original, SPARKWITCH$KNIFE_RANGE);
    }
}
