package dev.caecorthus.sparkwitch.client.mixin.sphinxhao;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No equip animation when the 「“手”枪」 is used (load, dry click or shot). {@code MinecraftClient.doItemUse} calls
 * {@code HeldItemRenderer.resetEquipProgress} after every accepted item use, which reads as a forward swing; Wathe
 * cancels it for its gun tag and {@code SaviorPistolEquipMixin} for the Savior pistol; this does the same for this gun.
 * 使用「“手”枪」（装填、空响或射击）时不播装备动画。{@code MinecraftClient.doItemUse} 在每次物品使用成功后调用
 * {@code HeldItemRenderer.resetEquipProgress}，看起来像向前挥了一下；Wathe 对自己的枪械标签、{@code SaviorPistolEquipMixin}
 * 对救世主手枪取消了这一调用；这里为这把枪做同样处理。
 */
@Mixin(MinecraftClient.class)
public abstract class SphinxHaoGunEquipMixin {
    @WrapWithCondition(
            method = "doItemUse()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;"
                            + "resetEquipProgress(Lnet/minecraft/util/Hand;)V"
            )
    )
    private boolean sparkwitch$keepSphinxHaoGunEquipProgress(HeldItemRenderer renderer, Hand hand) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null || !SphinxHaoGunInventory.isGun(player.getStackInHand(hand));
    }
}
