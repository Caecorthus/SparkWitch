package dev.caecorthus.sparkwitch.client.mixin.savior;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No equip animation when the Savior pistol is fired. {@code MinecraftClient.doItemUse} always calls
 * {@code HeldItemRenderer.resetEquipProgress} after an accepted item use, which plays the "item raises into the hand"
 * animation and reads as a forward swing. Wathe cancels the same call for its gun tag; this copies it for the pistol.
 * 救世主手枪开枪时不播装备动画。{@code MinecraftClient.doItemUse} 在物品使用成功后总会调用
 * {@code HeldItemRenderer.resetEquipProgress}，播放“物品抬回手中”的动画，看起来就像向前挥了一下。Wathe 对自己的枪械
 * 标签取消了同一调用；这里为手枪复制该处理。
 */
@Mixin(MinecraftClient.class)
public abstract class SaviorPistolEquipMixin {
    @WrapWithCondition(
            method = "doItemUse()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;"
                            + "resetEquipProgress(Lnet/minecraft/util/Hand;)V"
            )
    )
    private boolean sparkwitch$keepSaviorPistolEquipProgress(HeldItemRenderer renderer, Hand hand) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null || !player.getStackInHand(hand).isOf(SparkWitchItems.saviorPistol());
    }
}
