package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the restricted-only saved-alignment effective-faction override.
 * 仅在受限阶段按保存阵营覆盖有效阵营。
 */
final class WraithFactionService {
    private static boolean registered;

    private WraithFactionService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerEffectiveFactionResolver(WraithFactionService::resolve);
    }

    static @Nullable Identifier restrictedFaction(boolean restricted, @Nullable WraithState.Alignment alignment) {
        if (!restricted || alignment == null) {
            return null;
        }
        return alignment == WraithState.Alignment.GOOD ? FactionIds.CIVILIAN : FactionIds.KILLER;
    }

    private static @Nullable Identifier resolve(
            PlayerEntity player,
            GameWorldComponent gameComponent,
            Identifier currentFaction
    ) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.maybeGet(player).orElse(null);
        return wraith == null ? null : restrictedFaction(wraith.isRestricted(), wraith.getAlignment());
    }
}
