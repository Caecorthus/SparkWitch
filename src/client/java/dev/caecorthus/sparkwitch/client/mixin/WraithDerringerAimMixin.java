package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import dev.doctor4t.wathe.item.DerringerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Derringer shots pass through active Wraiths; DerringerItem hides RevolverItem's static selector.
 * 德林格选靶同样穿过冤魂；它的静态选靶方法独立于左轮，需要单独包裹。
 */
@Mixin(value = DerringerItem.class, remap = false)
public abstract class WraithDerringerAimMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$aimThroughWraiths(PlayerEntity user, Operation<HitResult> original) {
        return WraithAimPassThrough.selectAs(user, () -> original.call(user));
    }
}
