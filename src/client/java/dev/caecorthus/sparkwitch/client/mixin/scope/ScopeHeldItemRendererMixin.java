package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides only the local player's first-person hands and held items while scoped: HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, MatrixStack, Immediate, ClientPlayerEntity, int)} (never the third-person
 * overload, so other players and mobs still draw what they hold). Cancelling {@code GameRenderer#renderHand} instead
 * also skipped {@code InGameOverlayRenderer.renderOverlays}, so a scoped player inside a block saw through it. This one
 * method is what vanilla {@code renderHand} calls and what Iris's {@code HandRenderer} calls directly under a shader
 * pack, so the hands stay hidden with and without Iris, and the in-wall, underwater and fire overlays still draw.
 * Client-only presentation.
 * 开镜时只隐藏本地玩家的第一人称手与手持物品：作用于第一人称的
 * {@code HeldItemRenderer#renderItem(float, MatrixStack, Immediate, ClientPlayerEntity, int)} 的 HEAD（从不作用于第三人称
 * 重载，其他玩家与生物手中的物品照常绘制）。改为取消 {@code GameRenderer#renderHand} 会一并跳过
 * {@code InGameOverlayRenderer.renderOverlays}，导致开镜玩家在方块内能透视。原版 {@code renderHand} 调用的正是这个方法，
 * 开光影包时 Iris 的 {@code HandRenderer} 也直接调用它，因此无论有无 Iris 手都保持隐藏，墙内、水下与着火覆盖层照常绘制。
 * 纯客户端展示。
 */
@Mixin(HeldItemRenderer.class)
public abstract class ScopeHeldItemRendererMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"
            + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideScopedHands(float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider.Immediate vertexConsumers,
                                            ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (ScopeRuntime.hidesHand()) {
            ci.cancel();
        }
    }
}
