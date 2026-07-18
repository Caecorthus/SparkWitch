package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
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

/** Hides a Wraith's held items from ordinary observers. / 向普通观察者隐藏冤魂的双手物品。 */
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
                && (WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, player)
                || VendettaClientPresentation.shouldProjectSpectatorSteve(viewer, player))) {
            ci.cancel();
        }
    }
}
