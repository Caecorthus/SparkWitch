package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enforces Wathe's synchronized JumpConfig at the actual jump seam on client and server. */
@Mixin(LivingEntity.class)
public abstract class WraithJumpRestrictionMixin {
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$applyWraithJumpRestriction(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof PlayerEntity player) || !WraithStateService.isActive(player)) {
            return;
        }
        boolean mapAllowsJump = MapEnhancementsWorldComponent.KEY.get(player.getWorld())
                .getJumpConfig().allowed();
        if (!WraithParticipationRules.mayJump(true, mapAllowsJump)) {
            ci.cancel();
        }
    }
}
