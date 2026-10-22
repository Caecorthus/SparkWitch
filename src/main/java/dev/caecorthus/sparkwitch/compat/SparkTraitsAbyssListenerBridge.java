package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * External seam: the Abyss Listener's own optional SparkTraits reads, through the public {@code SparkTraitsApi}
 * facade only ({@code isLastStandPending(PlayerEntity)} and {@code hasActiveTrait(PlayerEntity, Identifier)} for
 * Conscience and Impostor); never {@code sparktraits.impl} or {@code sparktraits.component}. Lookups are cached and a
 * wrong shape is never trusted. Absent SparkTraits: not pending, both traits FALSE. Present but a method missing or
 * failing: not pending (the spectator gate still excludes a pending Last Stand player), and trait queries read as
 * unknown ({@code null}) so the caller fails closed.
 * 外部接缝：聆渊者自有的可选 SparkTraits 读取，仅经由公共门面 {@code SparkTraitsApi}（{@code isLastStandPending} 与
 * 针对良知、内鬼的 {@code hasActiveTrait}），从不触及 {@code impl} 或 {@code component} 包。查询结果被缓存，形状不符的方法
 * 永不被信任。未安装：非待定，两个词条均为 FALSE。已安装但方法缺失或出错：非待定（旁观判定仍会排除背水一战待定的玩家），
 * 词条查询视为未知（{@code null}），由调用方失败关闭。
 */
public final class SparkTraitsAbyssListenerBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    static final Identifier CONSCIENCE = Identifier.of(MOD_ID, "conscience");
    static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");

    private static final OptionalStaticMethod PENDING = resolve("isLastStandPending", PlayerEntity.class);
    private static final OptionalStaticMethod TRAIT = resolve("hasActiveTrait", PlayerEntity.class, Identifier.class);

    private SparkTraitsAbyssListenerBridge() {
    }

    /**
     * Whether SparkTraits Last Stand currently holds this player's pending death transition; absent, older or
     * failing providers read as false.
     * SparkTraits 背水一战当前是否持有该玩家的待决死亡转换；提供方缺失、过旧或出错时为 false。
     */
    public static boolean isLastStandPending(@Nullable PlayerEntity player) {
        return player != null && PENDING.callBoolean(false, false, false, player);
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

    private static OptionalStaticMethod resolve(String name, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(boolean.class);
    }
}
