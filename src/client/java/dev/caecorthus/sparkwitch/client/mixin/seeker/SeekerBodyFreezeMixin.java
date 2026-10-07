package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerBodyHold;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only: cancels the Seeker body's own SELF moves (walk, gravity, knockback velocity; the velocity is zeroed too)
 * while {@link SeekerBodyHold#freezes} holds it, i.e. while its chunks are missing on this client during or right after
 * a remote view. The body's position and on-ground flag therefore never change, so the movement packets it keeps
 * sending (it stays {@code isCamera()} while viewing) report exactly where the server left it. Piston and shulker
 * pushes and server teleports still apply. It can share {@code ClientPlayerEntity#move} HEAD with SparkStrength's
 * drone pilot hold (both cancellable injects, either may cancel). No-op outside a held Seeker body.
 * 纯客户端：当 {@link SeekerBodyHold#freezes} 保持本体时（即遥控观看期间或刚结束后其所在区块在本客户端缺失），取消搜寻者
 * 本体自身的 SELF 移动（行走、重力、击退速度；并清零速度）。本体的位置与着地标记因此不会改变，它继续发送的移动包（观看期间
 * 保持 {@code isCamera()}）恰好报告服务端留下的位置。活塞/潜影贝推动与服务端传送照常生效。可与 SparkStrength 无人机驾驶的
 * 本体保持共用 {@code ClientPlayerEntity#move} 的 HEAD（两者都是可取消注入，任一方都可取消）。搜寻者本体未被保持时不做任何事。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SeekerBodyFreezeMixin {
    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$holdBodyOverMissingChunks(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (type != MovementType.SELF) {
            return;
        }
        ClientPlayerEntity body = (ClientPlayerEntity) (Object) this;
        if (SeekerBodyHold.freezes(body)) {
            body.setVelocity(Vec3d.ZERO);
            ci.cancel();
        }
    }
}
