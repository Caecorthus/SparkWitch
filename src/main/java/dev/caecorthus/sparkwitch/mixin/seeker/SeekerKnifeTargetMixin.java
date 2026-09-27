package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client knife-stab target: nearest-wins device targeting for the charged right-click stab. Wraps the
 * {@code getKnifeTarget} call in {@code KnifeItem#onStoppedUsing} (vanilla override, remap = true; the Wathe static on
 * its {@code @At} is remap = false), which only runs the ray on the client. A Seeker device strictly nearer than the
 * original hit replaces it, so Wathe sends a {@code KnifeStabPayload} with the device id and
 * {@code SeekerKnifeStabDeviceMixin} validates and breaks it on the server; the player behind is not stabbed.
 * 客户端刀刺目标：右键蓄力刺击的“最近者命中”设备瞄准。包装 {@code KnifeItem#onStoppedUsing}（原版覆写，remap = true；
 * 其 {@code @At} 上的 Wathe 静态方法为 remap = false）中对 {@code getKnifeTarget} 的调用，该射线仅在客户端执行。
 * 若有严格比原命中更近的搜寻者设备则替换结果，Wathe 便以设备 id 发送 {@code KnifeStabPayload}，由服务端
 * {@code SeekerKnifeStabDeviceMixin} 校验并打坏；身后的玩家不会被刺中。
 */
@Mixin(KnifeItem.class)
public abstract class SeekerKnifeTargetMixin {
    /** Wathe's knife ray length; only a fallback. / Wathe 刀的射线长度，仅作兜底。 */
    @Unique
    private static final double SPARKWITCH$KNIFE_RANGE = 3.0;

    @WrapOperation(
            method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/item/KnifeItem;getKnifeTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;",
                    remap = false))
    private HitResult sparkwitch$preferNearerSeekerDevice(PlayerEntity attacker, Operation<HitResult> original) {
        return SeekerDeviceRaycast.preferNearerDevice(attacker, original.call(attacker), SPARKWITCH$KNIFE_RANGE);
    }
}
