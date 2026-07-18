package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the wide base model after other player model substitutions. / 在其他玩家模型替换后恢复宽臂基础模型。 */
@Mixin(value = PlayerEntityRenderer.class, priority = 100)
public abstract class WraithPlayerModelMixin extends LivingEntityRenderer<
        AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {
    @Unique
    private PlayerEntityModel<AbstractClientPlayerEntity> sparkwitch$basePlayerModel;

    protected WraithPlayerModelMixin(
            EntityRendererFactory.Context context,
            PlayerEntityModel<AbstractClientPlayerEntity> model,
            float shadowRadius
    ) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sparkwitch$rememberBasePlayerModel(
            EntityRendererFactory.Context context,
            boolean slim,
            CallbackInfo ci
    ) {
        this.sparkwitch$basePlayerModel = this.model;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD")
    )
    private void sparkwitch$restoreWidePlayerModel(
            AbstractClientPlayerEntity player,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (WraithSteveProjection.shouldAnonymizePlayer(player)
                && this.sparkwitch$basePlayerModel != null) {
            this.model = this.sparkwitch$basePlayerModel;
        }
    }
}
