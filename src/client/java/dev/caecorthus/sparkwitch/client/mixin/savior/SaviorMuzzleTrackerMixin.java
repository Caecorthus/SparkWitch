package dev.caecorthus.sparkwitch.client.mixin.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.savior.SaviorHandFlash;
import dev.caecorthus.sparkwitch.client.savior.SaviorMuzzleTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies Wathe's held-item muzzle hook for the Savior pistol: after the first-person item render every frame, convert
 * the hand matrix to a world position so the muzzle packet can spawn the flash exactly at the barrel. The hook only
 * answers for {@code sparkwitch:savior_pistol}; Wathe's own gun tag is not involved.
 * 为救世主手枪复制 Wathe 的手持物品枪口钩子：每帧第一人称物品渲染后，把手部矩阵换算成世界坐标，枪口数据包便能在枪管
 * 位置精确生成火焰。该钩子只处理 {@code sparkwitch:savior_pistol}，不涉及 Wathe 的枪械标签。
 */
@Mixin(HeldItemRenderer.class)
public abstract class SaviorMuzzleTrackerMixin {
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
    private void sparkwitch$recordSaviorMuzzle(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                               boolean leftHanded, MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // Render the hand-space flash before matrixToVec mutates the matrix (Wathe's order).
        // 在 matrixToVec 改动矩阵之前绘制手部火焰（与 Wathe 的顺序一致）。
        if (mode.isFirstPerson()) {
            SaviorHandFlash.render(matrices, vertexConsumers, light);
        }
        // Wathe only records a world muzzle position for the local player outside first person.
        // 与 Wathe 相同：本地玩家只在非第一人称时记录世界枪口位置。
        if (stack.isOf(SparkWitchItems.saviorPistol()) && entity instanceof PlayerEntity player
                && (player != MinecraftClient.getInstance().player || !mode.isFirstPerson())) {
            SaviorMuzzleTracker.set(player, SaviorMuzzleTracker.matrixToVec(matrices));
        }
    }
}
