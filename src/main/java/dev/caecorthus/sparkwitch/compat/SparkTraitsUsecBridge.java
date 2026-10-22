package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for USEC. Reflects only the public facade
 * {@code SparkTraitsApi.getMarksmanRangeMultiplier(PlayerEntity)}, {@code hasActiveTrait(PlayerEntity, Identifier)}
 * and the server-only {@code isHeavyArtilleryGunShot(ServerPlayerEntity, ServerPlayerEntity)}; an absent, older or
 * failing provider means base rifle range, the base bolt time and no Heavy Artillery layer. Both sides may call the
 * first two.
 * USEC 的可选 SparkTraits 接缝。仅反射公共门面 {@code getMarksmanRangeMultiplier}、{@code hasActiveTrait} 与仅服务端的
 * {@code isHeavyArtilleryGunShot}；提供方缺失、过旧或出错时，狙击枪使用基础射程、基础拉栓时间且没有重炮手额外层数。
 * 前两者两端均可调用。
 */
public final class SparkTraitsUsecBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier FAST_RELOAD = Identifier.of("sparktraits", "fast_reload");
    /** Mirrors UsecRules' Marksman bounds; kept local so this adapter has no role dependency.
     * 与 UsecRules 的精确枪手倍率上下限一致；在此本地保存，使适配器不依赖职业模块。 */
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 1.3;

    private static final OptionalStaticMethod MARKSMAN = resolve("getMarksmanRangeMultiplier", double.class,
            PlayerEntity.class);
    private static final OptionalStaticMethod TRAIT = resolve("hasActiveTrait", boolean.class, PlayerEntity.class,
            Identifier.class);

    private SparkTraitsUsecBridge() {
    }

    /**
     * SparkTraits Marksman range multiplier for the rifle, clamped to [1.0, 1.3]; any absence or failure is 1.0.
     * 狙击枪使用的 SparkTraits 精确枪手射程倍率，限制在 [1.0, 1.3]；任何缺失或失败均为 1.0。
     */
    public static double marksmanRangeMultiplier(@Nullable PlayerEntity player) {
        return player == null ? MIN_MULTIPLIER : clampMultiplier(MARKSMAN.callOrNull(player));
    }

    /**
     * Whether SparkTraits Fast Reload (快速装填) is active; false when SparkTraits is absent or the facade fails.
     * SparkTraits 快速装填是否生效；未安装 SparkTraits 或门面出错时为 false。
     */
    public static boolean hasFastReload(@Nullable PlayerEntity player) {
        return player != null && TRAIT.callBoolean(false, false, false, player, FAST_RELOAD);
    }

    /**
     * Server only (O3): whether SparkTraits counts this {@code wathe:gun_shot} kill of {@code victim} by
     * {@code shooter} as a Heavy Artillery shot (active trait on a gun-police role, within 5 blocks feet to feet), which
     * gives the AXMC one more shield layer. Ask it before the kill, because a death clears traits. Fails closed: an
     * absent or older SparkTraits, or a failing facade, is false.
     * 仅服务端（O3）：SparkTraits 是否把 {@code shooter} 以 {@code wathe:gun_shot} 击杀 {@code victim} 视为重炮手射击（持枪警职
     * 持有生效词条、脚对脚 5 格内），这会让 AXMC 多击穿一层护盾。须在击杀之前询问，因为死亡会清除词条。失败关闭：SparkTraits
     * 缺失、过旧或门面出错时为 false。
     */
    public static boolean isHeavyArtilleryGunShot(@Nullable ServerPlayerEntity shooter,
                                                  @Nullable ServerPlayerEntity victim) {
        return shooter != null && victim != null
                && HeavyArtillery.METHOD.callBoolean(false, false, false, shooter, victim);
    }

    /** Server-only query, held apart so the client (which shares the first two) never resolves it.
     * 仅服务端的查询，单独持有，使共用前两者的客户端从不解析它。 */
    private static final class HeavyArtillery {
        private static final OptionalStaticMethod METHOD = resolve("isHeavyArtilleryGunShot", boolean.class,
                ServerPlayerEntity.class, ServerPlayerEntity.class);
    }

    /** Non-numeric or non-finite values fail closed to 1.0. / 非数值或非有限值回退为 1.0。 */
    static double clampMultiplier(@Nullable Object raw) {
        if (!(raw instanceof Number number)) {
            return MIN_MULTIPLIER;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            return MIN_MULTIPLIER;
        }
        return Math.min(MAX_MULTIPLIER, Math.max(MIN_MULTIPLIER, value));
    }

    private static OptionalStaticMethod resolve(String name, Class<?> returnType, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(returnType).initializingClass();
    }
}
