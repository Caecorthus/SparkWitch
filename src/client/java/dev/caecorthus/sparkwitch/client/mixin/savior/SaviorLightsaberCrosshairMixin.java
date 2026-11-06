package dev.caecorthus.sparkwitch.client.mixin.savior;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the Savior lightsaber Wathe's bat crosshair, including the attack-cooldown bar. The renderer branches on
 * {@code stack.isOf(WatheItems.BAT)}; this wraps every {@code ItemStack.isOf} call in {@code renderCrosshair} and only
 * overrides the bat check, so the revolver/derringer/knife branches stay untouched.
 * 让救世主光剑使用 Wathe 球棒准星（含攻击冷却条）。渲染器用 {@code stack.isOf(WatheItems.BAT)} 分支；这里包住
 * {@code renderCrosshair} 里所有 {@code ItemStack.isOf} 调用，只改写球棒判定，不干扰左轮/德林加/刀分支。
 */
@Mixin(CrosshairRenderer.class)
public abstract class SaviorLightsaberCrosshairMixin {
    @WrapOperation(
            method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z")
    )
    private static boolean sparkwitch$lightsaberUsesBatCrosshair(ItemStack stack, Item item,
                                                                 Operation<Boolean> original) {
        if (original.call(stack, item)) {
            return true;
        }
        // The saber uses the bat crosshair; the pistol uses the revolver target crosshair, so aiming at a player
        // changes the pip exactly like Wathe's revolver. / 光剑用球棒准星；手枪用左轮的目标准星，瞄人时准星会变化。
        return (item == WatheItems.BAT && stack.isOf(SparkWitchItems.saviorLightsaber()))
                || (item == WatheItems.REVOLVER && stack.isOf(SparkWitchItems.saviorPistol()));
    }
}
