package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes elytra identity from the local anonymous Wraith projection. / 从本地冤魂的匿名投影中移除鞘翅身份信息。 */
@Mixin(ElytraFeatureRenderer.class)
public abstract class WraithElytraFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideProjectedElytra(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (entity instanceof PlayerEntity player
                && (WraithSteveProjection.shouldAnonymizePlayer(player)
                || WraithViewerRules.shouldHideFromViewer(
                        MinecraftClient.getInstance().player,
                        player
                ))) {
            ci.cancel();
        }
    }
}
