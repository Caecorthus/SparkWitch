package dev.caecorthus.sparkwitch.client.mixin.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecScopeLensAnchors;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens and closes the scope-lens holder around one player's render (HEAD / RETURN of the real
 * {@code render(AbstractClientPlayerEntity, ...)}, not the bridges): at HEAD the stack holds only the dispatcher's
 * translation to the render origin, so the lens recorded inside is relative to the player whatever the pass does
 * outside. Reads only; nothing is cancelled. Presentation only.
 * 在单个玩家的渲染前后打开与关闭物镜持有者（真正的 {@code render(AbstractClientPlayerEntity, ...)} 的 HEAD / RETURN，
 * 而非桥接方法）：HEAD 时矩阵栈只含分发器到渲染原点的平移，因此其中记录的物镜位置相对玩家本身，与本次渲染在外层做了
 * 什么无关。只读取，不取消任何东西。仅为表现。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class UsecScopeLensPlayerRenderMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void sparkwitch$openScopeLensHolder(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                int light, CallbackInfo ci) {
        UsecScopeLensAnchors.beginPlayer((PlayerEntityRenderer) (Object) this, player, tickDelta,
                matrices.peek().getPositionMatrix());
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void sparkwitch$closeScopeLensHolder(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, CallbackInfo ci) {
        UsecScopeLensAnchors.endPlayer();
    }
}
