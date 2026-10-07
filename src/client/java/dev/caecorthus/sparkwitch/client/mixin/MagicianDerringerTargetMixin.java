package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetAim;
import dev.doctor4t.wathe.item.DerringerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Derringer counterpart of {@code MagicianRevolverTargetMixin}: {@code DerringerItem} hides the revolver's static
 * selector with its own 7-block {@code getGunTarget}, so it needs its own wrapper.
 * {@code MagicianRevolverTargetMixin} 的德林加版本：{@code DerringerItem} 以自己 7 格的静态 {@code getGunTarget} 隐藏了
 * 左轮的选靶方法，需要单独包裹。
 */
@Mixin(value = DerringerItem.class, remap = false)
public abstract class MagicianDerringerTargetMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$preferNearerPuppet(PlayerEntity user, Operation<HitResult> original) {
        return MagicianPuppetAim.preferNearerPuppet(user, original.call(user), MagicianPuppetAim.DERRINGER_RANGE,
                false);
    }
}
