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
 * External Wathe seam (pinned in {@code watheClientMixinContracts}): gives the Savior lightsaber Wathe's bat crosshair,
 * including the attack-cooldown bar, and the Savior pistol Wathe's revolver crosshair. The renderer branches on
 * {@code stack.isOf(WatheItems.BAT)} / {@code isOf(WatheItems.REVOLVER)}; this wraps every {@code ItemStack.isOf} call in
 * {@code renderCrosshair} and only widens those two checks, so the derringer and knife branches stay untouched. The
 * pistol fires with the revolver's own pick ({@code SaviorClient}), so its crosshair is exactly the revolver's: the
 * same cooldown check on the held item and the same {@code getVisibleGunTarget} target pip, with the Magician puppet
 * and Raven Form crow seams that sit on it.
 * 外部 Wathe 接缝（已固定在 {@code watheClientMixinContracts}）：让救世主光剑使用 Wathe 球棒准星（含攻击冷却条），救世主手枪
 * 使用 Wathe 左轮准星。渲染器用 {@code stack.isOf(WatheItems.BAT)} / {@code isOf(WatheItems.REVOLVER)} 分支；这里包住
 * {@code renderCrosshair} 里所有 {@code ItemStack.isOf} 调用，只放宽这两处判定，不干扰德林加与刀分支。手枪开火使用左轮自身的
 * 选靶（{@code SaviorClient}），因此其准星与左轮完全一致：对手持物品相同的冷却判断，以及相同的 {@code getVisibleGunTarget}
 * 目标准星，连同其上的魔术师皮套与化鸦乌鸦接缝。
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
