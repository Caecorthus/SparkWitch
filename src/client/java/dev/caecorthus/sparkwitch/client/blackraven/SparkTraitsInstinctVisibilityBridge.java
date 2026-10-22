package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import net.minecraft.entity.player.PlayerEntity;

/** Optional bridge to SparkTraits' stable visibility facade only. / 仅连接 SparkTraits 稳定可见性门面的可选桥。 */
public final class SparkTraitsInstinctVisibilityBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of(MOD_ID, API_CLASS,
            "isInstinctHidden", PlayerEntity.class, PlayerEntity.class);

    private SparkTraitsInstinctVisibilityBridge() {
    }

    public static boolean isHidden(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null && target != null && IS_INSTINCT_HIDDEN.callBoolean(false, false, false, viewer, target);
    }
}
