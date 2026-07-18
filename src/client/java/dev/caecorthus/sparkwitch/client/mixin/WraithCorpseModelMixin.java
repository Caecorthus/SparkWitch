package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithSteveProjection;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the wide corpse model after disguise hooks. / 在伪装 hook 后恢复宽臂尸体模型。 */
@Mixin(value = PlayerBodyEntityRenderer.class, priority = 100)
public abstract class WraithCorpseModelMixin
        extends LivingEntityRenderer<PlayerBodyEntity, PlayerEntityModel<PlayerBodyEntity>> {
    @Unique
    private PlayerEntityModel<PlayerBodyEntity> sparkwitch$baseBodyModel;

    protected WraithCorpseModelMixin(
            EntityRendererFactory.Context context,
            PlayerEntityModel<PlayerBodyEntity> model,
            float shadowRadius
    ) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sparkwitch$rememberBaseBodyModel(
            EntityRendererFactory.Context context,
            boolean slim,
            CallbackInfo ci
    ) {
        this.sparkwitch$baseBodyModel = this.model;
    }

    @Inject(
            method = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD")
    )
    private void sparkwitch$restoreWideBodyModel(
            PlayerBodyEntity body,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (WraithSteveProjection.shouldAnonymizeCorpses()
                && this.sparkwitch$baseBodyModel != null) {
            this.model = this.sparkwitch$baseBodyModel;
        }
    }
}
