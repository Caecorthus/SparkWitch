package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * External seam: the Knight's optional SparkTraits reads, through the public {@code SparkTraitsApi} facade only
 * ({@code hasActiveTrait(PlayerEntity, Identifier)} for Conscience and Impostor); never {@code sparktraits.impl} or
 * {@code sparktraits.component}. Lookups are cached and a wrong shape is never trusted. Absent SparkTraits: both
 * traits FALSE. Present but a method missing or failing: unknown ({@code null}), and callers fail closed.
 * 外部接缝：骑士自有的可选 SparkTraits 读取，仅经由公共门面 {@code SparkTraitsApi}（针对良知、内鬼的
 * {@code hasActiveTrait}），从不触及 {@code impl} 或 {@code component} 包。查询结果被缓存，形状不符的方法永不信任。
 * 未安装：两个词条均为 FALSE。已安装但方法缺失或出错：未知（{@code null}），调用方失败关闭。
 */
public final class KnightTraitsBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    static final Identifier CONSCIENCE = Identifier.of(MOD_ID, "conscience");
    static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");

    private static final OptionalStaticMethod TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS, "hasActiveTrait",
            PlayerEntity.class, Identifier.class).returning(boolean.class);

    private KnightTraitsBridge() {
    }

    /** Conscience trait: FALSE when SparkTraits is absent, null when unknown. / 良知词条：未安装为 FALSE，未知为 null。 */
    public static @Nullable Boolean isConscienceActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, CONSCIENCE);
    }

    /** Impostor trait: FALSE when SparkTraits is absent, null when unknown. / 内鬼词条：未安装为 FALSE，未知为 null。 */
    public static @Nullable Boolean isImpostorActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, IMPOSTOR);
    }

    private static @Nullable Boolean hasActiveTrait(@Nullable PlayerEntity player, Identifier traitId) {
        if (!TRAIT.isModLoaded()) {
            return Boolean.FALSE;
        }
        return player == null ? null : TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, traitId);
    }
}
