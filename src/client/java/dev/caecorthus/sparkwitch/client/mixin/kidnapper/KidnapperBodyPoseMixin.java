package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperBodyPose;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
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

    /**
     * Wathe writes {@code riding} on the skin model only (the skeleton model never gets it), so the
     * carried skin must not take the vanilla seated pose. / Wathe 只写皮肤模型的 {@code riding}
     * （骨架模型从不设置），被携带的皮肤不能进入原版坐姿。
     */
    @WrapOperation(
            method = RENDER_BODY,
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/entity/model/PlayerEntityModel;riding:Z",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void sparkwitch$keepKidnappedSkinFlat(
            PlayerEntityModel<PlayerBodyEntity> model,
            boolean riding,
            Operation<Void> original,
            @Local(argsOnly = true) PlayerBodyEntity body
    ) {
        original.call(model, KidnapperBodyPose.skinModelRiding(riding, kidnapperCarrier(body) != null));
    }

    private static @Nullable PlayerEntity kidnapperCarrier(PlayerBodyEntity body) {
        Entity vehicle = body.getVehicle();
        boolean hasDirectPlayerCarrier = vehicle instanceof PlayerEntity;
        boolean isExactKidnapper = hasDirectPlayerCarrier
                && KidnapperRules.isKidnapper(GameWorldComponent.KEY.get(vehicle.getWorld()).getRole((PlayerEntity) vehicle));
        return KidnapperBodyPose.isEligible(hasDirectPlayerCarrier, isExactKidnapper) ? (PlayerEntity) vehicle : null;
    }

    private static @Nullable KidnapperBodyPose poseFor(PlayerBodyEntity body, float tickDelta) {
        PlayerEntity carrier = kidnapperCarrier(body);
        if (carrier == null) {
            return null;
        }
        return KidnapperBodyPose.fromCarrierRotation(
                carrier.prevYaw,
                carrier.getYaw(),
                carrier.prevPitch,
                carrier.getPitch(),
                tickDelta
        );
    }
}
