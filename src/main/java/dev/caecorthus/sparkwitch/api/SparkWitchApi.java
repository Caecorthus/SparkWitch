package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable, null-safe public queries for optional downstream integrations.
 * 为可选下游集成提供稳定且支持空值的公共查询。
 */
public final class SparkWitchApi {
    private SparkWitchApi() {
    }

    public static boolean isWraithActive(@Nullable PlayerEntity player) {
        return WraithStateService.isActive(player);
    }

    public static boolean isWraithRestricted(@Nullable PlayerEntity player) {
        return WraithStateService.isRestricted(player);
    }

    public static boolean isWraithPromoted(@Nullable PlayerEntity player) {
        return WraithStateService.isPromoted(player);
    }
}
