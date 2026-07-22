package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Read-only access to synchronized Wraith runtime state. */
public final class WraithStateService {
    private WraithStateService() {
    }

    public static boolean isActive(@Nullable PlayerEntity player) {
        return player != null && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isActive).orElse(false);
    }

    public static boolean isRestricted(@Nullable PlayerEntity player) {
        return player != null && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted).orElse(false);
    }

    public static boolean isPromoted(@Nullable PlayerEntity player) {
        return player != null && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isPromoted).orElse(false);
    }
}
