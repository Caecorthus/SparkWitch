package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.Wathe;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Keeps active Wraith jumping authoritative even when Wathe maps disable jumping. */
@Mixin(value = LivingEntity.class, priority = 1200)
public abstract class WraithJumpRestrictionMixin {
    @Unique
    private static final EntityAttributeModifier WRAITH_JUMP_REENABLE_MODIFIER = new EntityAttributeModifier(
            SparkWitch.id("wraith_jump_reenable_modifier"),
            1.0,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    @Shadow
    protected abstract float getJumpVelocity();

    @Shadow
    protected abstract float getJumpBoostVelocityModifier();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$applyWraithJumpRestriction(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof PlayerEntity player) || !WraithStateService.isActive(player)) {
            return;
        }
        if (!WraithParticipationRules.mayJump(true, false)) {
            ci.cancel();
            return;
        }
        sparkwitch$jumpAsWraith(player);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sparkwitch$restoreWraithJumpStrength(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        EntityAttributeInstance jumpStrength = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
        if (jumpStrength == null) {
            return;
        }
        boolean hasWatheDisable = jumpStrength.hasModifier(Wathe.id("disable_jump_modifier"));
        boolean hasWraithCompensation = jumpStrength.hasModifier(WRAITH_JUMP_REENABLE_MODIFIER.id());
        boolean shouldCompensate = WraithStateService.isActive(player) && hasWatheDisable;
        if (shouldCompensate && !hasWraithCompensation) {
            // Wathe 通过 -100% 跳跃力度实现地图禁跳；冤魂需要完全无视该配置，
            // 因此只在 Wathe 禁跳 modifier 存在时补一个 +100% 临时 modifier 抵消它。
            jumpStrength.addTemporaryModifier(WRAITH_JUMP_REENABLE_MODIFIER);
        } else if (!shouldCompensate && hasWraithCompensation) {
            jumpStrength.removeModifier(WRAITH_JUMP_REENABLE_MODIFIER.id());
        }
    }

    @Unique
    private void sparkwitch$jumpAsWraith(PlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, this.getJumpVelocity() + this.getJumpBoostVelocityModifier(), velocity.z);
        if (player.isSprinting()) {
            float yawRadians = player.getYaw() * (float) (Math.PI / 180.0D);
            // 这里复刻原版跳跃的疾跑前冲。活跃冤魂在 HEAD 手动完成跳跃后会取消原方法，
            // 从而避开 Wathe 后续注入的地图禁跳取消逻辑，同时保留玩家熟悉的跳跃手感。
            player.setVelocity(player.getVelocity().add(
                    -MathHelper.sin(yawRadians) * 0.2F,
                    0.0D,
                    MathHelper.cos(yawRadians) * 0.2F
            ));
        }
        player.velocityModified = true;
    }
}
