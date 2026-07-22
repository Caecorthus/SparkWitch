package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adds only the killer's privately synced Vendetta target to NoellesRoles' native screen list. */
@Mixin(AssassinScreen.class)
public abstract class AssassinVendettaTargetMixin {
    // NoellesRoles 1.7.6 ships this inherited Screen#init override under its intermediary name.
    @Redirect(
            method = "method_25426",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getAllAlivePlayers()Ljava/util/List;"
            ),
            require = 1
    )
    private List<UUID> sparkwitch$appendBoundVendetta(GameWorldComponent game) {
        List<UUID> targets = new ArrayList<>(game.getAllAlivePlayers());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return targets;
        }
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            VendettaPlayerComponent component = VendettaPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (component != null && component.isBoundViewer() && !targets.contains(player.getUuid())) {
                targets.add(player.getUuid());
            }
        }
        return targets;
    }
}
