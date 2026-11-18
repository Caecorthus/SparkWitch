package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla's bow sway keeps sampling {@code sin((useTicks - 0.1) * 1.3)} while the bow stays drawn, and since
 * {@code useTicks} keeps growing past the 20-tick full charge, the first-person bow trembles the whole time. The
 * M1903A4 is a bow-class weapon, so that tremble is dropped here for this item once the charge is full.
 * 原版弓的第一人称摆动在拉满后仍按 {@code sin((useTicks - 0.1) * 1.3)} 持续采样，而 {@code useTicks} 拉满后还在增长，
 * 于是弓会一直轻微抖动。M1903A4 是弓类物品，这里在蓄力拉满后取消这件物品的这段抖动。
 */
@Mixin(HeldItemRenderer.class)
public abstract class M1903A4ChargeWobbleMixin {

    /**
     * The tremble is the only translate in {@code renderFirstPersonItem} whose x and z are zero with a tiny y, so it
     * can be told apart without touching any other sway or equip offset.
     * 这段抖动是该渲染方法里唯一 x、z 为 0 且 y 极小的位移，因此可以与其他摆动、装备偏移区分开。
     */
    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V")
    )
    private void sparkwitch$skipFullChargeWobble(MatrixStack matrices, float x, float y, float z) {
        if (sparkwitch$isDrawnM1903a4()) {
            if (x == 0.0F && z == 0.0F && y != 0.0F && Math.abs(y) <= 0.0045F) {
                return;
            }
        }
        matrices.translate(x, y, z);
    }

    @Unique
    private boolean sparkwitch$isDrawnM1903a4() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null
                && player.isUsingItem()
                && player.getItemUseTime() >= 20
                && player.getActiveItem().isOf(SparkWitchItems.m1903a4());
    }
}
