package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Captures NoellesRoles swallowed state before its AFTER listener clears the component. */
record WraithSwallowedCapture(boolean swallowed, @Nullable WraithReturnPoint taotieLocation) {
    static WraithSwallowedCapture capture(ServerPlayerEntity victim) {
        SwallowedPlayerComponent component = SwallowedPlayerComponent.KEY.get(victim);
        if (!component.isSwallowed()) {
            return new WraithSwallowedCapture(false, null);
        }
        UUID taotieUuid = component.getSwallowedBy();
        ServerPlayerEntity taotie = taotieUuid == null
                ? null
                : victim.getServer().getPlayerManager().getPlayer(taotieUuid);
        if (taotie == null) {
            return new WraithSwallowedCapture(true, null);
        }
        return new WraithSwallowedCapture(true, new WraithReturnPoint(
                taotie.getServerWorld().getRegistryKey(),
                taotie.getPos(),
                taotie.getYaw(),
                taotie.getPitch()
        ));
    }
}
