package dev.caecorthus.sparkwitch.client.mixin.hunter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.hunter.DoubleBarrelMuzzleFlash;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the shotgun's first-person hand flash in the matrix the shotgun was just rendered with, at the point where
 * Wathe draws its revolver hand flash. Render only: the world muzzle position for observers and for the shooter in
 * third person is Wathe's own, because the shotgun is in {@code wathe:guns} and Wathe's hook at this same point
 * already records it (with the revolver's offset). Wathe's hook translates this matrix without popping it, so this
 * hook never records a position of its own: a second translate would add both offsets.
 * 在猎枪刚渲染完的矩阵中、即 Wathe 绘制左轮手部火焰的同一位置绘制猎枪第一人称手部火焰。只负责绘制：观察者与第三人称射手
 * 看到的世界枪口位置沿用 Wathe 自己的记录，因为猎枪在 {@code wathe:guns} 中，Wathe 在同一注入点的钩子已按左轮偏移记录它。
 * Wathe 的钩子平移该矩阵后不会弹出，因此本钩子绝不自行记录位置：再平移一次会把两个偏移叠加。
 */
@Mixin(HeldItemRenderer.class)
public abstract class DoubleBarrelHandFlashMixin {
    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;"
                    + "Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
                    + "ZLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem("
                            + "Lnet/minecraft/entity/LivingEntity;"
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
                            + "ZLnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                            + "Lnet/minecraft/world/World;III)V",
                    shift = At.Shift.AFTER
            )
    )
    private void sparkwitch$drawDoubleBarrelHandFlash(LivingEntity entity, ItemStack stack,
                                                      ModelTransformationMode mode, boolean leftHanded,
                                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                      int light, CallbackInfo ci) {
        // First-person modes only ever render the local player's own hands.
        // 第一人称模式只会渲染本地玩家自己的手。
        if (mode.isFirstPerson() && stack.isOf(SparkWitchItems.doubleBarrelShotgun())) {
            DoubleBarrelMuzzleFlash.render(matrices, vertexConsumers, leftHanded);
        }
    }
}
