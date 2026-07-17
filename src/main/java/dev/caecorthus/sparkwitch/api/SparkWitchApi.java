package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import net.minecraft.entity.player.PlayerEntity;

/** Public read-only facade for downstream Wraith compatibility. / 面向下游冤魂兼容的只读公共门面。 */
public final class SparkWitchApi {
    private SparkWitchApi() {
    }

    public static boolean isWraithActive(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isActive)
                .orElse(false);
    }

    public static boolean isWraithRestricted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted)
                .orElse(false);
    }
}
