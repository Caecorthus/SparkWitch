package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * External seam: one public static method of an optional mod's facade (in practice {@code SparkTraitsApi}), reached
 * by reflection with no compile-time dependency. JDK and Fabric Loader only, so both sides may use it. The mod check
 * and the lookup each run at most once (the mod set and classpath are fixed once the game runs) and are then latched,
 * success or failure, behind a volatile read. Every call names its own answer for each failure mode, so a bridge keeps
 * its exact fail-open or fail-closed semantics:
 * <ul>
 *     <li><b>absent</b>: the gating mod is not loaded (never for {@link #ungated});</li>
 *     <li><b>unavailable</b>: the class or method is missing, or fails the {@link #returning} shape check;</li>
 *     <li><b>thrown</b>: the lookup succeeded but this invocation failed (the target threw, or reflection
 *     refused).</li>
 * </ul>
 * Warnings are opt-in ({@link #warnOnce}); without one every failure is silent.
 * 外部接缝：可选模组门面（实际为 {@code SparkTraitsApi}）上的一个公共静态方法，通过反射访问，无编译期依赖。仅依赖 JDK 与
 * Fabric Loader，因此两端均可使用。模组检查与方法查找各至多执行一次（游戏运行后模组集合与类路径固定），无论成败都在一次
 * volatile 读取之后锁存。每次调用为每种失败方式给出自己的答案，使各桥接保持原有的放行或拒绝语义：
 * 缺失（门控模组未加载，{@link #ungated} 无此情形）、不可用（类或方法缺失，或未通过 {@link #returning} 形状检查）、
 * 抛出（查找成功但本次调用失败：目标抛出异常或反射拒绝）。警告需显式开启（{@link #warnOnce}），否则所有失败均静默。
 */
public final class OptionalStaticMethod {
    private static final Object ABSENT = new Object();
    private static final Object UNAVAILABLE = new Object();
    private static final Object THROWN = new Object();

    private final String modId;
    private final String className;
    private final String name;
    private final Class<?>[] parameters;
    private final Class<?> returnType;
    private final boolean initializeClass;
    private final Consumer<Throwable> warning;
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile Boolean modLoaded;
    private volatile Lookup lookup;

    private OptionalStaticMethod(String modId, String className, String name, Class<?>[] parameters,
                                 Class<?> returnType, boolean initializeClass, Consumer<Throwable> warning) {
        this.modId = modId;
        this.className = Objects.requireNonNull(className);
        this.name = Objects.requireNonNull(name);
        this.parameters = parameters.clone();
        this.returnType = returnType;
        this.initializeClass = initializeClass;
        this.warning = warning;
    }

    /** Gated on {@code modId} being loaded: when it is not, every call answers its "absent" fallback without a lookup.
     * 以 {@code modId} 已加载为门控：未加载时每次调用都直接返回“缺失”回退值，不做查找。 */
    public static OptionalStaticMethod of(String modId, String className, String name, Class<?>... parameters) {
        return new OptionalStaticMethod(Objects.requireNonNull(modId), className, name, parameters, null, false, null);
    }

    /** No mod check: a missing class simply reads as unavailable. / 不检查模组：类缺失即视为不可用。 */
    public static OptionalStaticMethod ungated(String className, String name, Class<?>... parameters) {
        return new OptionalStaticMethod(null, className, name, parameters, null, false, null);
    }

    /** Trusts only a static method with exactly this return type; any other shape is unavailable (no exception, so a
     * {@link #warnOnce} warning receives null). Without it any public method of that name and signature is used.
     * 只信任返回类型恰好为该类型的静态方法；其他形状视为不可用（无异常，因此 {@link #warnOnce} 收到 null）。
     * 未设置时使用该名称与签名的任意公共方法。 */
    public OptionalStaticMethod returning(Class<?> type) {
        return new OptionalStaticMethod(modId, className, name, parameters, Objects.requireNonNull(type),
                initializeClass, warning);
    }

    /** Loads the class with {@code Class.forName(className)}, which runs its static initialiser, instead of the default
     * {@code Class.forName(className, false, loader)}; this keeps the exact form a migrated bridge used.
     * 使用会执行静态初始化的 {@code Class.forName(className)} 加载类，而不是默认的
     * {@code Class.forName(className, false, loader)}；用于保持迁移前桥接使用的确切形式。 */
    public OptionalStaticMethod initializingClass() {
        return new OptionalStaticMethod(modId, className, name, parameters, returnType, true, warning);
    }

    /** Reports the first lookup or call failure, then stays silent. The argument is the failure, or null when the
     * method exists with the wrong shape. Never called while the gating mod is absent.
     * 报告第一次查找或调用失败，之后保持静默。参数为失败原因；方法存在但形状不符时为 null。门控模组缺失时从不调用。 */
    public OptionalStaticMethod warnOnce(Consumer<Throwable> warning) {
        return new OptionalStaticMethod(modId, className, name, parameters, returnType, initializeClass,
                Objects.requireNonNull(warning));
    }

    /** Whether the gating mod is loaded (always true when {@link #ungated}); asked once, a throwing loader reads as
     * not loaded. / 门控模组是否已加载（{@link #ungated} 时恒为 true）；只询问一次，加载器抛出异常时视为未加载。 */
    public boolean isModLoaded() {
        if (modId == null) {
            return true;
        }
        Boolean loaded = modLoaded;
        if (loaded == null) {
            // Racing threads compute the same answer, so the write needs no lock. / 并发线程算出相同结果，写入无需加锁。
            loaded = detectModLoaded(modId);
            modLoaded = loaded;
        }
        return loaded;
    }

    /** The resolved method, or null when the mod is absent or the method unavailable (for bespoke invocation).
     * 已解析的方法；模组缺失或方法不可用时为 null（供特殊调用方式使用）。 */
    public Method method() {
        return isModLoaded() ? resolvedMethod() : null;
    }

    /** The raw result, or null when the mod is absent, the method unavailable or the call thrown.
     * 原始返回值；模组缺失、方法不可用或调用抛出时为 null。 */
    public Object callOrNull(Object... args) {
        Object result = outcome(args);
        return result == ABSENT || result == UNAVAILABLE || result == THROWN ? null : result;
    }

    /** {@code Boolean.TRUE.equals(result)} on success, otherwise the fallback for that failure mode.
     * 成功时为 {@code Boolean.TRUE.equals(result)}，否则为对应失败方式的回退值。 */
    public boolean callBoolean(boolean whenAbsent, boolean whenUnavailable, boolean whenThrown, Object... args) {
        Object result = outcome(args);
        if (result == ABSENT) {
            return whenAbsent;
        }
        if (result == UNAVAILABLE) {
            return whenUnavailable;
        }
        if (result == THROWN) {
            return whenThrown;
        }
        return Boolean.TRUE.equals(result);
    }

    /** The {@code Boolean} result on success (null for any other value), otherwise the fallback for that failure mode;
     * any fallback may be null. / 成功时为 {@code Boolean} 结果（其他值为 null），否则为对应失败方式的回退值；回退值可为 null。 */
    public Boolean callNullableBoolean(Boolean whenAbsent, Boolean whenUnavailable, Boolean whenThrown,
                                       Object... args) {
        Object result = outcome(args);
        if (result == ABSENT) {
            return whenAbsent;
        }
        if (result == UNAVAILABLE) {
            return whenUnavailable;
        }
        if (result == THROWN) {
            return whenThrown;
        }
        return result instanceof Boolean value ? value : null;
    }

    /** Whether the call completed (a void method counts); false when absent, unavailable or thrown.
     * 调用是否完成（void 方法亦可）；缺失、不可用或抛出时为 false。 */
    public boolean callVoid(Object... args) {
        Object result = outcome(args);
        return result != ABSENT && result != UNAVAILABLE && result != THROWN;
    }

    private Object outcome(Object[] args) {
        if (!isModLoaded()) {
            return ABSENT;
        }
        Method method = resolvedMethod();
        if (method == null) {
            return UNAVAILABLE;
        }
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(error);
            return THROWN;
        }
    }

    private Method resolvedMethod() {
        Lookup current = lookup;
        if (current == null) {
            synchronized (this) {
                current = lookup;
                if (current == null) {
                    current = new Lookup(find());
                    lookup = current;
                }
            }
        }
        return current.method();
    }

    private Method find() {
        Method method;
        try {
            // Same defining loader as every bridge class (one mod jar), so this equals the bridge's own forName.
            // 与各桥接类的定义加载器相同（同一模组 jar），因此等同于桥接自身的 forName。
            method = Class.forName(className, initializeClass, OptionalStaticMethod.class.getClassLoader())
                    .getMethod(name, parameters);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(error);
            return null;
        }
        if (returnType != null && (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != returnType)) {
            warn(null);
            return null;
        }
        return method;
    }

    private void warn(Throwable error) {
        if (warning != null && warned.compareAndSet(false, true)) {
            warning.accept(error);
        }
    }

    private static boolean detectModLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private record Lookup(Method method) {
    }
}
