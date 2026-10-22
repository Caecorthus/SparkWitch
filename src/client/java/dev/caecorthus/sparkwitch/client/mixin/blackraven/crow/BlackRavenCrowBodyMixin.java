package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only hold of the Black Raven's body while it flies the crow (Seeker remote-view approach). {@code isCamera}:
 * vanilla sends the body's movement packets only while it is the camera, and the server ends Raven Form if the body
 * drifts, so the bound body keeps answering true and reports its frozen (zero-input) position. {@code move} HEAD:
 * the body's own SELF moves (walk, knockback velocity) are cancelled for the whole session and afterwards until its
 * chunks are back (see {@code BlackRavenCrowPilotClient.freezesBody}); piston pushes and server teleports still apply.
 * One exception during the session: a body frozen in mid-air (the freeze lands up to a round trip after the server
 * started the session, so a jump or a ledge can leave it airborne) still falls straight down under gravity until it
 * lands, while its chunks are loaded and it is out of water; its horizontal motion is dropped. The server follows that
 * fall (its anchor grace and falling rule) instead of ending the session for an ungrounded body. Both handlers chain
 * with the Seeker's and SparkStrength's drone hooks on the same methods. First statement of each handler: the
 * {@code isActive()} / hold gate.
 * 黑羽鸦操控乌鸦期间对本体的纯客户端保持（搜寻者遥控视角做法）。isCamera：原版只在本体是镜头时发送其移动包，而本体漂移会让服务端结束
 * 化鸦，因此被绑定的本体持续返回 true，上报其冻结（零输入）的位置。move 开头：本体自身的 SELF 移动（行走、击退速度）在整个会话期间
 * 被取消，会话结束后保持到其区块送达；活塞推动与服务端传送照常生效。会话期间唯一的例外：在半空中被冻结的本体（冻结最多在服务端开始
 * 会话一个往返之后才生效，跳跃或走下台阶都可能让它悬空）在其区块已加载且不在水中时，仍受重力竖直下落直到落地，水平运动被舍弃。
 * 服务端跟随这次下落（锚点宽限与下落规则），而不会因本体离地结束会话。两个处理器都可与搜寻者及 SparkStrength 无人机在同一方法上的
 * 钩子串联。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class BlackRavenCrowBodyMixin {
    /** Re-entry guard of the vertical-only fall step below. / 下方仅竖直下落一步的重入保护。 */
    @Unique
    private static boolean sparkwitch$crowBodyFallStep;

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
        if (type != MovementType.SELF || sparkwitch$crowBodyFallStep) {
            return;
        }
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (!BlackRavenCrowPilotClient.freezesBody(body)) {
            return;
        }
        ci.cancel();
        if (BlackRavenCrowPilotClient.isPilotingBody(body) && !body.isOnGround() && !body.isTouchingWater()
                && movement.y < 0.0 && crowBodyChunksLoaded(body)) {
            // Fall straight down: the inner move collides and lands as vanilla; travel() then adds gravity.
            // 竖直下落：内层移动照原版计算碰撞与落地；随后 travel() 叠加重力。
            body.setVelocity(0.0, body.getVelocity().y, 0.0);
            sparkwitch$crowBodyFallStep = true;
            try {
                body.move(MovementType.SELF, new Vec3d(0.0, movement.y, 0.0));
            } finally {
                sparkwitch$crowBodyFallStep = false;
            }
            return;
        }
        body.setVelocity(Vec3d.ZERO);
    }

    /**
     * Every chunk under the body's box is present ({@code ClientWorld.isChunkLoaded} is always true in 1.21.1, so the
     * chunk manager is asked). / 本体箱体下的每个区块都已存在（1.21.1 的 ClientWorld.isChunkLoaded 恒为真，因此询问区块管理器）。
     */
    @Unique
    private static boolean crowBodyChunksLoaded(ClientPlayerEntity body) {
        Box box = body.getBoundingBox();
        ChunkManager chunks = body.getWorld().getChunkManager();
        for (int x = ChunkSectionPos.getSectionCoord(box.minX); x <= ChunkSectionPos.getSectionCoord(box.maxX); x++) {
            for (int z = ChunkSectionPos.getSectionCoord(box.minZ); z <= ChunkSectionPos.getSectionCoord(box.maxZ); z++) {
                if (!chunks.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
