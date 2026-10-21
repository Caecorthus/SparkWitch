package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only hold of the Black Raven's body while it flies the crow (Seeker remote-view approach). {@code isCamera}:
 * vanilla sends the body's movement packets only while it is the camera, and the server ends Raven Form if the body
 * drifts, so the bound body keeps answering true and reports its frozen (zero-input) position. {@code move} HEAD:
 * the body's own SELF moves (walk, gravity, knockback velocity) are cancelled for the whole session and afterwards
 * until its chunks are back (see {@code BlackRavenCrowPilotClient.freezesBody}); piston pushes and server teleports
 * still apply. Both chain with the Seeker's and SparkStrength's drone hooks on the same methods. First statement of
 * each handler: the {@code isActive()} / hold gate.
 * 黑羽鸦操控乌鸦期间对本体的纯客户端保持（搜寻者遥控视角做法）。isCamera：原版只在本体是镜头时发送其移动包，而本体漂移会让服务端结束
 * 化鸦，因此被绑定的本体持续返回 true，上报其冻结（零输入）的位置。move 开头：本体自身的 SELF 移动（行走、重力、击退速度）在整个会话期间
 * 被取消，会话结束后保持到其区块送达；活塞推动与服务端传送照常生效。两者都可与搜寻者及 SparkStrength 无人机在同一方法上的钩子串联。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class BlackRavenCrowBodyMixin {
    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$crowBodyStaysCamera(CallbackInfoReturnable<Boolean> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        if (BlackRavenCrowPilotClient.isPilotingBody(this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$holdCrowBody(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (type != MovementType.SELF) {
            return;
        }
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (BlackRavenCrowPilotClient.freezesBody(body)) {
            body.setVelocity(Vec3d.ZERO);
            ci.cancel();
        }
    }
}
