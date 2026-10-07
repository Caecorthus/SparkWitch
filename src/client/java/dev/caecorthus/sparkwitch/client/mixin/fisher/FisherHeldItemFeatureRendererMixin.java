package dev.caecorthus.sparkwitch.client.mixin.fisher;

import dev.caecorthus.sparkwitch.client.fisher.FisherGlimmerClient;
import dev.caecorthus.sparkwitch.client.fisher.FisherGlimmerClientRules;
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

/**
 * Vanilla invisibility still renders held items; a glimmering player's hands vanish with the body for every
 * non-spectator viewer (the Wraith mixin on the same method keeps its own rule). Presentation only.
 * 原版隐身仍会渲染手持物；灵光中玩家的手持物随身体一起对所有非旁观者消失（同一方法上的冤魂 mixin 保持自己的规则）。
 * 仅用于展示。
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class FisherHeldItemFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideGlimmeringHeldItems(
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
        if (entity instanceof PlayerEntity holder && viewer != null
                && FisherGlimmerClientRules.hidesHeldItems(FisherGlimmerClient.isGlimmering(holder), viewer.isSpectator())) {
            ci.cancel();
        }
    }
}
