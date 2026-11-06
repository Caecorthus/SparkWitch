package dev.caecorthus.sparkwitch.client.mixin.hunter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.hunter.DoubleBarrelMuzzleFlash;
import dev.doctor4t.wathe.util.MatrixParticleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand-space first-person flash and world muzzle position for the double-barrel shotgun, mirroring Wathe's held-item
 * hook. Wathe's own hook only answers for the {@code wathe:guns} tag, which the shotgun deliberately stays out of (it
 * must not inherit Wathe's gun pickup and penalty rules). The world position is stored in Wathe's
 * {@link MatrixParticleManager} so Wathe's existing muzzle packet spawns its yellow {@code GUNSHOT} particle exactly
 * at the barrel, both for observers and for the local shooter in third person.
 * 双管猎枪的手部空间第一人称火焰与世界枪口位置，对应 Wathe 的手持物品钩子。Wathe 自带钩子只处理
 * {@code wathe:guns} 标签，而猎枪刻意不在该标签内（避免继承 Wathe 的枪械拾取与惩罚规则）。世界位置写入 Wathe 的
 * {@link MatrixParticleManager}，由 Wathe 现有枪口数据包在枪管处生成其黄色 {@code GUNSHOT} 粒子，观察者与
 * 第三人称下的本地射手都适用。
 */
@Mixin(HeldItemRenderer.class)
public abstract class DoubleBarrelMuzzleTrackerMixin {
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
    private void sparkwitch$recordDoubleBarrelMuzzle(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                                     boolean leftHanded, MatrixStack matrices,
                                                     VertexConsumerProvider vertexConsumers, int light,
                                                     CallbackInfo ci) {
        if (!stack.isOf(SparkWitchItems.doubleBarrelShotgun())) {
            return;
        }
        // Render the hand-space flash before matrixToVec mutates the matrix. / 在 matrixToVec 改动矩阵之前绘制手部火焰。
        if (mode.isFirstPerson()) {
            DoubleBarrelMuzzleFlash.render(matrices, vertexConsumers, light);
        }
        // Wathe only records a world muzzle position for the local player outside first person.
        // 与 Wathe 相同：本地玩家只在非第一人称时记录世界枪口位置。
        if (entity instanceof PlayerEntity player
                && (player != MinecraftClient.getInstance().player || !mode.isFirstPerson())) {
            MatrixParticleManager.setMuzzlePosForPlayer(player, matrixToVec(matrices));
        }
    }

    /** Wathe's matrix conversion with the shotgun muzzle offset instead of the revolver's. / Wathe 的矩阵换算，偏移改为猎枪枪口。 */
    private static Vec3d matrixToVec(MatrixStack matrices) {
        matrices.translate(0.0F, 0.0485F, -0.3296F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vector4f origin = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        matrix.transform(origin);
        Vec3d cameraPos = camera.getPos();
        return new Vec3d(cameraPos.x + origin.x(), cameraPos.y + origin.y(), cameraPos.z + origin.z());
    }
}
