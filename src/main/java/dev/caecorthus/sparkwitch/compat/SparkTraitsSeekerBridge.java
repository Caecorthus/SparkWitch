package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * External seam: the Seeker's own optional SparkTraits reads through the public {@code SparkTraitsApi} facade only
 * ({@code isLastStandPending(PlayerEntity)} and {@code hasActiveTrait(PlayerEntity, Identifier)}); never
 * {@code sparktraits.impl} or {@code sparktraits.component}. Lookups are cached; a wrong shape is never trusted.
 * Absent SparkTraits: not pending, not impostor. Present but a method missing or failing: not pending; impostor
 * query reads as unknown (true), which denies devices and pay (Q5-b, fail closed).
 * 外部接缝：搜寻者自有的可选 SparkTraits 读取，仅经由公共门面 {@code SparkTraitsApi}
 * （{@code isLastStandPending} 与 {@code hasActiveTrait}），从不触及 {@code impl} 或 {@code component} 包。查询结果被缓存，
 * 形状不符的方法永不被信任。未安装：非待定、非内鬼。已安装但方法缺失或失败：非待定；内鬼查询视为未知（true），
 * 从而拒绝设备与报酬（Q5-b，失败即关闭）。
 */
public final class SparkTraitsSeekerBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    private static final Logger LOGGER = LoggerFactory.getLogger("sparkwitch");
    // Older SparkTraits builds lack this facade: silently "not pending". / 旧版缺少此门面：静默视为非待定。
    private static final OptionalStaticMethod PENDING = resolve("isLastStandPending", PlayerEntity.class);
    private static final OptionalStaticMethod TRAIT = resolve("hasActiveTrait", PlayerEntity.class, Identifier.class)
            .warnOnce(error -> LOGGER.warn(
                    "Seeker cannot verify the optional Impostor trait; devices and task pay are denied", error));

    private SparkTraitsSeekerBridge() {
    }

    /**
     * Whether SparkTraits Last Stand currently holds this player's pending death transition. Absent, older or
     * failing providers read as false, so the final-death fallback then treats a dead Seeker as finally dead.
     * SparkTraits 背水一战当前是否持有该玩家的待决死亡转换。提供方缺失、过旧或出错时为 false，
     * 此时最终死亡兜底会把已死亡的搜寻者视为最终死亡。
     */
    public static boolean isLastStandPending(@Nullable PlayerEntity player) {
        return player != null && PENDING.callBoolean(false, false, false, player);
    }

    /**
     * Whether the Impostor trait is active or cannot be verified. False when SparkTraits is absent; true when it is
     * present but the facade is missing, malformed or throws, or when no player is given.
     * 内鬼词条是否生效或无法确认。未安装 SparkTraits 时为 false；已安装但门面缺失、形状不符、抛出异常，
     * 或未提供玩家时为 true。
     */
    public static boolean isImpostorOrUnknown(@Nullable PlayerEntity player) {
        if (!TRAIT.isModLoaded()) {
            return false;
        }
        if (player == null) {
            return true;
        }
        // Unknown (null: missing, malformed or throwing facade, or a non-Boolean answer) counts as Impostor.
        // 未知（null：门面缺失、形状不符或抛出，或返回非布尔值）按内鬼处理。
        return !Boolean.FALSE.equals(TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR));
    }

    private static OptionalStaticMethod resolve(String name, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(boolean.class);
    }
}
