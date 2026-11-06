package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for the top-left mood flags ({@code client/moodflag/MoodFlags}): whether the local player
 * holds Conscience or Impostor. Reflects only the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)}; never {@code sparktraits.impl} or
 * {@code sparktraits.component}. Display only and fail closed: absent SparkTraits, a missing method, an exception or a
 * null player all read as false, so Wathe's own flag stays.
 * 左上角心情旗帜（{@code client/moodflag/MoodFlags}）的可选 SparkTraits 接缝：本地玩家是否持有善良或内鬼词条。仅反射
 * 公共门面 {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)}，从不触及 {@code impl} 或 {@code component}
 * 包。仅用于展示且失败关闭：未安装、方法缺失、抛出异常或玩家为空时一律视为 false，保留 Wathe 自己的旗帜。
 */
public final class SparkTraitsMoodFlagBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    static final Identifier CONSCIENCE = Identifier.of(MOD_ID, "conscience");
    static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
                    "hasActiveTrait", PlayerEntity.class, Identifier.class)
            .returning(boolean.class)
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Mood flags cannot read the optional Conscience/Impostor traits; Wathe's flag is kept", error));

    private SparkTraitsMoodFlagBridge() {
    }

    /** Conscience (善良): false unless the facade confirms it. / 善良词条：门面确认前一律为 false。 */
    public static boolean isConscienceActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, CONSCIENCE);
    }

    /** Impostor (内鬼): false unless the facade confirms it. / 内鬼词条：门面确认前一律为 false。 */
    public static boolean isImpostorActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, IMPOSTOR);
    }

    private static boolean hasActiveTrait(@Nullable PlayerEntity player, Identifier traitId) {
        return player != null && HAS_ACTIVE_TRAIT.callBoolean(false, false, false, player, traitId);
    }
}
