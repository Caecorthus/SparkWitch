package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional SparkTraits seam for USEC. Reflects only the public facade
 * {@code SparkTraitsApi.getMarksmanRangeMultiplier(PlayerEntity)} and {@code hasActiveTrait(PlayerEntity, Identifier)};
 * an absent, older or failing provider means base rifle range and the base bolt time. Both sides may call it.
 * USEC 的可选 SparkTraits 接缝。仅反射公共门面 {@code getMarksmanRangeMultiplier} 与 {@code hasActiveTrait}；
 * 提供方缺失、过旧或出错时，狙击枪使用基础射程与基础拉栓时间。两端均可调用。
 */
public final class SparkTraitsUsecBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier FAST_RELOAD = Identifier.of("sparktraits", "fast_reload");
    /** Mirrors UsecRules' Marksman bounds; kept local so this adapter has no role dependency.
     * 与 UsecRules 的精确枪手倍率上下限一致；在此本地保存，使适配器不依赖职业模块。 */
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 1.3;

    private static volatile boolean marksmanResolved;
    private static volatile @Nullable Method marksmanMethod;
    private static volatile boolean traitResolved;
    private static volatile @Nullable Method traitMethod;

    private SparkTraitsUsecBridge() {
    }

    /**
     * SparkTraits Marksman range multiplier for the rifle, clamped to [1.0, 1.3]; any absence or failure is 1.0.
     * 狙击枪使用的 SparkTraits 精确枪手射程倍率，限制在 [1.0, 1.3]；任何缺失或失败均为 1.0。
     */
    public static double marksmanRangeMultiplier(@Nullable PlayerEntity player) {
        if (player == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return MIN_MULTIPLIER;
        }
        Method method = marksmanMethod();
        if (method == null) {
            return MIN_MULTIPLIER;
        }
        try {
            return clampMultiplier(method.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return MIN_MULTIPLIER;
        }
    }

    /**
     * Whether SparkTraits Fast Reload (快速装填) is active; false when SparkTraits is absent or the facade fails.
     * SparkTraits 快速装填是否生效；未安装 SparkTraits 或门面出错时为 false。
     */
    public static boolean hasFastReload(@Nullable PlayerEntity player) {
        if (player == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        Method method = traitMethod();
        if (method == null) {
            return false;
        }
        try {
            return method.invoke(null, player, FAST_RELOAD) instanceof Boolean active && active;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
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

    private static @Nullable Method marksmanMethod() {
        if (!marksmanResolved) {
            synchronized (SparkTraitsUsecBridge.class) {
                if (!marksmanResolved) {
                    marksmanMethod = resolve("getMarksmanRangeMultiplier", double.class, PlayerEntity.class);
                    marksmanResolved = true;
                }
            }
        }
        return marksmanMethod;
    }

    private static @Nullable Method traitMethod() {
        if (!traitResolved) {
            synchronized (SparkTraitsUsecBridge.class) {
                if (!traitResolved) {
                    traitMethod = resolve("hasActiveTrait", boolean.class, PlayerEntity.class, Identifier.class);
                    traitResolved = true;
                }
            }
        }
        return traitMethod;
    }

    private static @Nullable Method resolve(String name, Class<?> returnType, Class<?>... parameters) {
        try {
            Method method = Class.forName(API_CLASS).getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType ? method : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
