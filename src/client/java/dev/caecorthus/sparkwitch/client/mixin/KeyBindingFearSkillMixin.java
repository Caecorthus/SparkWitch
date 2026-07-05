package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hooks.GrandWitchFearClientHooks;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the shared role ability key before role UIs or packets can fire.
 * 在角色界面或技能包触发前拦截共享能力键。
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingFearSkillMixin {
    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$blockFearedRoleAbilityPress(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockRoleAbilityKey((KeyBinding) (Object) this, cir.getReturnValue())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$blockFearedRoleAbilityHold(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockRoleAbilityKey((KeyBinding) (Object) this, cir.getReturnValue())) {
            cir.setReturnValue(false);
        }
    }
}
