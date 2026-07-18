package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperBodyPose;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Client-only Wathe render adapter for Kidnapper's carried corpse. /
 * 绑架者携带尸体的 Wathe 纯客户端渲染适配器。
 */
@Mixin(value = PlayerBodyEntityRenderer.class, remap = false)
public abstract class KidnapperBodyPoseMixin {
    private static final String RENDER_BODY = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/model/BipedEntityModel;Lnet/minecraft/client/render/RenderLayer;FF)V";

    @ModifyArgs(
            method = RENDER_BODY,
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/render/entity/PlayerBodyEntityRenderer;setupTransforms(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V"
            )
    )
    private void sparkwitch$alignKidnappedBodyYaw(Args args) {
        PlayerBodyEntity body = args.get(0);
        KidnapperBodyPose pose = poseFor(body, args.get(4));
        if (pose != null) {
            args.set(3, pose.bodyYaw());
        }
    }

    @ModifyArgs(
            method = RENDER_BODY,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V"
            )
    )
    private void sparkwitch$alignKidnappedBodyHead(
            Args args,
            PlayerBodyEntity body,
            float bodyYaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            BipedEntityModel<PlayerBodyEntity> model,
            RenderLayer layer,
            float limbAngle,
            float limbDistance
    ) {
        KidnapperBodyPose pose = poseFor(body, tickDelta);
        if (pose != null) {
            args.set(4, pose.relativeHeadYaw());
            args.set(5, pose.headPitch());
        }
    }

    private static @Nullable KidnapperBodyPose poseFor(PlayerBodyEntity body, float tickDelta) {
        Entity vehicle = body.getVehicle();
        boolean hasDirectPlayerCarrier = vehicle instanceof PlayerEntity;
        boolean isExactKidnapper = hasDirectPlayerCarrier
                && KidnapperRules.isKidnapper(GameWorldComponent.KEY.get(vehicle.getWorld()).getRole((PlayerEntity) vehicle));
        if (!KidnapperBodyPose.isEligible(hasDirectPlayerCarrier, isExactKidnapper)) {
            return null;
        }
        PlayerEntity carrier = (PlayerEntity) vehicle;
        return KidnapperBodyPose.fromCarrierRotation(
                carrier.prevYaw,
                carrier.getYaw(),
                carrier.prevPitch,
                carrier.getPitch(),
                tickDelta
        );
    }
}
