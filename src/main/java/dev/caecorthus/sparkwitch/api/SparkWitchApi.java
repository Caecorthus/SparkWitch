package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
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

    /**
     * Returns whether the player is an active Wraith whose owner-visible saved alignment is KILLER.
     * Redacted client records and invalid persisted state fail closed rather than leaking an alignment guess.
     */
    public static boolean isKillerAlignedWraith(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(wraith -> isKillerAlignedWraith(wraith.isActive(), wraith.getAlignment()))
                .orElse(false);
    }

    static boolean isKillerAlignedWraith(boolean active, WraithState.Alignment alignment) {
        return active && alignment == WraithState.Alignment.KILLER;
    }

    public static boolean isWraithRestricted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted)
                .orElse(false);
    }

    public static boolean isWraithPromoted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isPromoted)
                .orElse(false);
    }
}
