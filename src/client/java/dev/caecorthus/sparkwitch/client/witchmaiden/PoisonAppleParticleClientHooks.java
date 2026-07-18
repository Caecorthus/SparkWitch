package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.player.PlayerEntity;

/** Reuses Wathe's complete native poison visibility decision without adding a second role list. */
public final class PoisonAppleParticleClientHooks {
    private PoisonAppleParticleClientHooks() {
    }

    public static boolean canSee(PlayerEntity player) {
        return player != null && (WatheClient.isKiller()
                || WatheClient.canSeeSpectatorInformation()
                || CanSeePoison.EVENT.invoker().visible(player));
    }
}
