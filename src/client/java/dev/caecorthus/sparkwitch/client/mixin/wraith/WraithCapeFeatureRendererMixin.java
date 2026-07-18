package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes cape identity from the local anonymous Wraith projection. / 从本地冤魂的匿名投影中移除披风身份信息。 */
@Mixin(CapeFeatureRenderer.class)
public abstract class WraithCapeFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideProjectedCape(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (WraithSteveProjection.shouldAnonymizePlayer(player)
                || WraithViewerRules.shouldHideFromViewer(
                        MinecraftClient.getInstance().player,
                        player
                )) {
            ci.cancel();
        }
    }
}
