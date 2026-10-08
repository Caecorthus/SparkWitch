package dev.caecorthus.sparkwitch.client.mixin.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecFireInput;
import dev.caecorthus.sparkwitch.client.usec.UsecRecoil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purely visual USEC recoil kick: at every return of {@code tiltViewWhenHurt} (the view-bob stack that vanilla folds
 * into the world projection and the hand pass), a decaying upward pitch is appended while the local player's own
 * camera is active. The player's rotation, the crosshair ray and the fire packet's aim never change.
 * 纯视觉的 USEC 后坐抖动：在 {@code tiltViewWhenHurt} 的每个返回点（原版将该视角晃动栈并入世界投影与手部绘制），只要
 * 本地玩家自己的镜头处于激活状态，就追加一个逐渐衰减的上扬俯仰。玩家朝向、准星射线与开火数据包的朝向都不会改变。
 */
@Mixin(GameRenderer.class)
public abstract class UsecRecoilCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("RETURN"))
    private void sparkwitch$applyUsecRecoil(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getCameraEntity() != client.player) {
            return;
        }
        float kick = UsecRecoil.currentKickDegrees(tickDelta, UsecFireInput.recoilFovMultiplier());
        if (kick != 0.0F) {
            // A negative X rotation of the view stack pitches the view up. / 视角栈绕 X 轴负向旋转即向上仰。
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-kick));
        }
    }
}
