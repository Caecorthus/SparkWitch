package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Hides the Savior from NoellesRoles' Assassin guess panel; the server also ignores guesses naming it. */
@Mixin(AssassinScreen.class)
public abstract class AssassinSaviorGuessMixin {
    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @Inject(method = "getAllGuessableRoles", at = @At("RETURN"), cancellable = true, require = 1)
    private void sparkwitch$hideSaviorFromAssassinGuess(CallbackInfoReturnable<List<Role>> cir) {
        List<Role> roles = cir.getReturnValue();
        if (roles == null || roles.isEmpty()) {
            return;
        }
        boolean found = false;
        for (Role role : roles) {
            if (SaviorRules.isSavior(role)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }
        List<Role> filtered = new ArrayList<>(roles.size());
        for (Role role : roles) {
            if (!SaviorRules.isSavior(role)) {
                filtered.add(role);
            }
        }
        cir.setReturnValue(filtered);
    }
}
