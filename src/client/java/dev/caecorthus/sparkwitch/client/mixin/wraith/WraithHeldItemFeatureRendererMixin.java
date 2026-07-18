package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides held items from anonymous projections and prevents an invisible Wraith's hands leaking. / 隐藏匿名投影的手持物，并阻止隐身冤魂通过手部物品泄漏。 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class WraithHeldItemFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideWraithHeldItems(
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
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (entity instanceof PlayerEntity player
                && (WraithSteveProjection.shouldAnonymizePlayer(player)
                || WraithViewerRules.shouldHideFromViewer(viewer, player))) {
            ci.cancel();
        }
    }
}
