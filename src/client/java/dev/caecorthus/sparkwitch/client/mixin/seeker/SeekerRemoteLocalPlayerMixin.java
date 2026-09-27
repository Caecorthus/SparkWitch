package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla copies input into the body and sends its movement packets only while {@code isCamera()}; with the camera
 * on the remote focus the body would stop reporting and drift. While possessing, the real (bound) body keeps
 * answering true, so it keeps its packet flow with the frozen (zero) input. First statement: the isActive() gate.
 * 原版只在 {@code isCamera()} 为真时才把输入写入本体并发送其移动包；相机位于遥控焦点时本体会停止上报并漂移。
 * 附身期间，真实（绑定的）本体始终返回 true，从而以冻结（为零）的输入保持数据包流。第一条语句为 isActive() 门槛。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SeekerRemoteLocalPlayerMixin {
    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$bodyStaysCamera(CallbackInfoReturnable<Boolean> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        if (SeekerRemoteViewClient.isPossessingBody(this)) {
            cir.setReturnValue(true);
        }
    }
}
