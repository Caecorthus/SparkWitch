package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.ricochet.RysTDazeClient;
import dev.caecorthus.sparkwitch.client.bear.ricochet.RysTDazeCurve;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purely visual Rys-T ricochet jolt (D5): at every return of {@code tiltViewWhenHurt} (the view-bob stack vanilla
 * folds into the world projection and the hand pass) the daze's roll, pitch and yaw are appended while the local
 * player's own camera is active. The player's rotation, crosshair ray and every aim the server sees never change.
 * Amplitudes already include vanilla's Distortion Effects scale ({@link RysTDazeClient#pose}).
 * 纯视觉的 Rys-T 跳弹震动（D5）：在 {@code tiltViewWhenHurt}（原版并入世界投影与手部绘制的视角晃动栈）的每个返回点，只要
 * 本地玩家自己的镜头处于激活状态，就追加眩晕的翻滚、俯仰与偏航。玩家朝向、准星射线及服务端看到的任何瞄准都不会改变。幅度
 * 已乘以原版“视觉扭曲效果”比例（{@link RysTDazeClient#pose}）。
 */
@Mixin(GameRenderer.class)
public abstract class RicochetDazeCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("RETURN"))
    private void sparkwitch$applyRysTDaze(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (!RysTDazeClient.active()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getCameraEntity() != client.player) {
            return;
        }
        RysTDazeCurve.Pose pose = RysTDazeClient.pose(tickDelta);
        if (pose.hasJolt()) {
            // View-stack X/Y rotations by +angle tip the view down / turn it right (Minecraft pitch/yaw signs).
            // 视角栈绕 X/Y 轴正向旋转即低头/右转（与 Minecraft 俯仰/偏航符号一致）。
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.roll()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.yaw()));
        }
    }
}
