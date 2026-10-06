package dev.caecorthus.sparkwitch.client.mixin.recruitment;

import dev.caecorthus.sparkwitch.client.grandwitch.RecruitmentHoldClient;
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
 * Vanilla invisibility still renders held items; a recruit in the Grand Witch recruitment hold loses them with the
 * body for every non-spectator viewer (the Wraith and Fisher mixins on the same method keep their own rules).
 * Presentation only.
 * 原版隐身仍会渲染手持物；处于大魔女招募定身中的新共犯，其手持物随身体一起对所有非旁观者消失（同一方法上的
 * 冤魂与钓鱼佬 mixin 保持各自的规则）。仅用于展示。
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class RecruitmentHoldHeldItemFeatureRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$hideHeldRecruitItems(
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
        if (entity instanceof PlayerEntity holder && RecruitmentHoldClient.hidesHeldItems(holder)) {
            ci.cancel();
        }
    }
}
