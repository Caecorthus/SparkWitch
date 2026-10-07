package dev.caecorthus.sparkwitch.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for the Black Raven disguise economy; reflects only the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)} for Conscience and Impostor.
 * FALSE when SparkTraits is absent; null when the facade is missing or fails (callers pay nothing).
 * 黑羽鸦伪装经济的可选 SparkTraits 接缝；仅反射公共门面 hasActiveTrait 查询良知与内鬼词条。
 * 未安装 SparkTraits 时为 FALSE；门面缺失或出错时为 null（调用方不发放）。
 */
public final class SparkTraitsBlackRavenBridge {
    static final String MOD_ID = "sparktraits";
    static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    static final Identifier CONSCIENCE = Identifier.of(MOD_ID, "conscience");
    static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");

    private static volatile @Nullable Method hasActiveTrait;
    private static volatile boolean lookupFailed;

    private SparkTraitsBlackRavenBridge() {
    }

    public static @Nullable Boolean isConscienceActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, CONSCIENCE);
    }

    public static @Nullable Boolean isImpostor(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, IMPOSTOR);
    }

    private static @Nullable Boolean hasActiveTrait(@Nullable PlayerEntity player, Identifier traitId) {
        if (player == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return Boolean.FALSE;
        }
        Method method = method();
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, player, traitId) instanceof Boolean active ? active : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Method method() {
        Method cached = hasActiveTrait;
        if (cached != null || lookupFailed) {
            return cached;
        }
        synchronized (SparkTraitsBlackRavenBridge.class) {
            if (hasActiveTrait == null && !lookupFailed) {
                try {
                    hasActiveTrait = Class.forName(API_CLASS, false, SparkTraitsBlackRavenBridge.class.getClassLoader())
                            .getMethod("hasActiveTrait", PlayerEntity.class, Identifier.class);
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                    lookupFailed = true;
                }
            }
            return hasActiveTrait;
        }
    }
}
