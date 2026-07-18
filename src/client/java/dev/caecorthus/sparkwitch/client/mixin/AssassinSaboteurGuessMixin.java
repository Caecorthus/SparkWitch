package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithProgression;
import dev.doctor4t.wathe.api.Role;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AssassinScreen.class)
public abstract class AssassinSaboteurGuessMixin {
    /**
     * Redirects only NoellesRoles' native-killer check so Saboteur remains guessable in place.
     * 仅重定向 NoellesRoles 的原生杀手检查，让破坏者保持原列表顺序并可被猜测。
     */
    @Redirect(
            method = "getAllGuessableRoles",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/Role;canUseKiller()Z")
    )
    private boolean sparkwitch$preserveSaboteurGuess(Role role) {
        return WraithProgression.shouldExcludeFromAssassinGuess(role);
    }
}
