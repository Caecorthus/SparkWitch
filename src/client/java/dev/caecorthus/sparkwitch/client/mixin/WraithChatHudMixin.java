package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WatheClient.class, remap = false)
public final class WraithChatHudMixin {
    private WraithChatHudMixin() {
    }

    @Inject(method = "shouldDisableChat()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sparkwitch$enforceWraithChatPolicy(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (!SparkWitchServerConnection.isConfirmedServer()
                || player == null
                || !WraithClientState.isActive(player)) {
            return;
        }

        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        cir.setReturnValue(!WraithParticipationRules.mayUseTextChat(
                true,
                GuardianAngelRules.isGuardianAngel(role),
                player.isCreative()
        ));
    }
}
