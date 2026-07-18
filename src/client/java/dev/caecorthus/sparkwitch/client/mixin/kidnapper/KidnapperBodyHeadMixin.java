package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Changes only the carried corpse head pose; the lying body transform stays untouched. / 只修改被搬运尸体的头部姿态，平躺身体变换保持不变。 */
@Mixin(PlayerBodyEntityRenderer.class)
public abstract class KidnapperBodyHeadMixin {
    @ModifyArgs(
            method = "render(Ldev/doctor4t/wathe/entity/PlayerBodyEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/model/BipedEntityModel;Lnet/minecraft/client/render/RenderLayer;FF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V"
            )
    )
    private void sparkwitch$followKidnapperView(Args args) {
        PlayerBodyEntity body = args.get(0);
        if (!(body.getVehicle() instanceof PlayerEntity carrier)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(carrier.getWorld()).getRole(carrier);
        if (!KidnapperRules.isKidnapper(role)) {
            return;
        }

        float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        float carrierYaw = MathHelper.lerpAngleDegrees(delta, carrier.prevYaw, carrier.getYaw());
        float bodyYaw = MathHelper.lerpAngleDegrees(delta, body.prevBodyYaw, body.bodyYaw);
        float carrierPitch = MathHelper.lerp(delta, carrier.prevPitch, carrier.getPitch());
        args.set(4, carrierYaw - bodyYaw);
        args.set(5, carrierPitch);
    }
}
