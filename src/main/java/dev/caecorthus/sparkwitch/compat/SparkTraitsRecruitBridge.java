package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for the Recruit. Reflects only the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)}; never {@code sparktraits.impl} or
 * {@code sparktraits.component}. Fails closed: an absent, older or failing provider reads as "not Impostor", so the
 * Recruit keeps its knife sanity cost and innocent-kill death.
 * 新兵的可选 SparkTraits 接缝。仅反射公共门面 {@code SparkTraitsApi.hasActiveTrait}，从不触及 {@code impl} 或
 * {@code component} 包。失败关闭：提供方缺失、过旧或出错时视为“非内鬼”，新兵保留用刀理智消耗与误杀处死。
 */
public final class SparkTraitsRecruitBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");
    private static final OptionalStaticMethod TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS, "hasActiveTrait",
            PlayerEntity.class, Identifier.class).returning(boolean.class).initializingClass();

    private SparkTraitsRecruitBridge() {
    }

    /** Whether the SparkTraits Impostor trait is active; false on any absence or failure.
     * SparkTraits 内鬼词条是否生效；任何缺失或失败均为 false。 */
    public static boolean isImpostor(@Nullable PlayerEntity player) {
        return player != null && TRAIT.callBoolean(false, false, false, player, IMPOSTOR);
    }
}
