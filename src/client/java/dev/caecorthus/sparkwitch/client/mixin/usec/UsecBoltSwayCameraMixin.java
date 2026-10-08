package dev.caecorthus.sparkwitch.client.mixin.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecBoltSway;
import dev.caecorthus.sparkwitch.client.usec.UsecBoltSwayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purely visual scoped AXMC bolt sway, the recoil kick's seam: at every return of {@code tiltViewWhenHurt} (the view
 * stack vanilla folds into the world projection and the hand pass) the current sway is appended as a view rotation
 * while the local player's own camera is active. It adds on top of the hurt tilt, the recoil kick, bobbing (applied
 * after this stack) and FOV changes. The PiP lens pass never calls this method; it reuses the main projection captured
 * at {@code WorldRenderer#render} HEAD, so the lens shows the same rotation, magnified. Reads are side-effect free
 * (state advances once per client tick), so both calls per frame agree. The player's rotation, the crosshair ray and
 * the fire packet's aim never change.
 * 纯视觉的开镜 AXMC 拉栓晃动，沿用后坐抖动的接缝：在 {@code tiltViewWhenHurt} 的每个返回点（原版将该视角栈并入世界投影与
 * 手部绘制），只要本地玩家自己的镜头处于激活状态，就把当前晃动作为视角旋转追加上去。它叠加在受伤倾斜、后坐抖动、视角摇晃
 * （在此栈之后应用）与视野变化之上。画中画镜内渲染从不调用该方法，而是复用在 {@code WorldRenderer#render} HEAD 捕获的主投影，
 * 因此镜内显示同一旋转并被放大。读取没有副作用（状态每个客户端刻推进一次），所以每帧的两次调用结果一致。玩家朝向、准星射线
 * 与开火数据包的朝向都不会改变。
 */
@Mixin(GameRenderer.class)
public abstract class UsecBoltSwayCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("RETURN"))
    private void sparkwitch$applyUsecBoltSway(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getCameraEntity() != client.player) {
            return;
        }
        UsecBoltSway.Angles sway = UsecBoltSwayClient.currentAngles(tickDelta);
        if (sway.isZero()) {
            return;
        }
        // View-space rotations: +Y turns the view right, -X tilts it up, +Z rolls the camera clockwise.
        // 视角空间旋转：绕 +Y 视角右转，绕 -X 视角上抬，绕 +Z 镜头顺时针滚转。
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sway.yaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-sway.pitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sway.roll()));
    }
}
