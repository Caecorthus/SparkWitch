package dev.caecorthus.sparkwitch.client.insider;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * External seam (optional SparkTraits): the Insider's own client read of the public {@code SparkTraitsApi} facade,
 * {@code isInstinctHidden(viewer, target)}; never SparkTraits' implementation or component packages. Fail-closed means
 * "not hidden": an absent SparkTraits, an older build without the method, a wrong shape or a throwing call changes
 * nothing and never breaks the client.
 * 外部接缝（可选 SparkTraits）：内应自有的客户端读取，仅经由公共门面 {@code SparkTraitsApi} 的
 * {@code isInstinctHidden(viewer, target)}，从不触及 SparkTraits 的实现或组件包。失败关闭即“不隐藏”：SparkTraits 缺失、
 * 旧版缺少方法、形状不符或调用抛异常时都不改变任何结果，也绝不会让客户端崩溃。
 */
public final class InsiderSparkTraitsBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    private static volatile boolean hiddenResolved;
    private static volatile @Nullable Method hiddenMethod;

    private InsiderSparkTraitsBridge() {
    }

    /** SparkTraits hides the target from this viewer's instinct. / SparkTraits 是否对该观察者的本能隐藏目标。 */
    public static boolean isInstinctHidden(@Nullable PlayerEntity viewer, @Nullable PlayerEntity target) {
        if (viewer == null || target == null || !isLoaded()) {
            return false;
        }
        Method method = hiddenMethod();
        return method != null && invoke(method, viewer, target);
    }

    private static boolean isLoaded() {
        return Loaded.VALUE;
    }

    /**
     * The mod set is fixed once the game runs, so the loader is asked once, on first use (holder idiom), instead of on
     * every outline query.
     * 游戏运行后模组集合固定，因此只在首次使用时询问一次加载器（持有者惯用法），而不是每次描边查询都询问。
     */
    private static final class Loaded {
        private static final boolean VALUE = detect();

        private static boolean detect() {
            try {
                return FabricLoader.getInstance().isModLoaded(MOD_ID);
            } catch (LinkageError | RuntimeException ignored) {
                return false;
            }
        }
    }

    private static boolean invoke(Method method, Object... args) {
        try {
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static @Nullable Method hiddenMethod() {
        if (!hiddenResolved) {
            synchronized (InsiderSparkTraitsBridge.class) {
                if (!hiddenResolved) {
                    hiddenMethod = resolve("isInstinctHidden", PlayerEntity.class, PlayerEntity.class);
                    hiddenResolved = true;
                }
            }
        }
        return hiddenMethod;
    }

    private static @Nullable Method resolve(String name, Class<?>... parameters) {
        try {
            Method method = Class.forName(API_CLASS, false, InsiderSparkTraitsBridge.class.getClassLoader())
                    .getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
