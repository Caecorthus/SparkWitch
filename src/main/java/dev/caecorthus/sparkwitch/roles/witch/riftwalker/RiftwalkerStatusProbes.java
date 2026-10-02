package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Riftwalker-shared "held in place" probes. Gate entry (C10) refuses a player rooted by a Hunter trap or held by the
 * SparkStrength capture-device stun (entry would be a free escape). Witches' Sabbath (D19) pulls such teammates and
 * they stay held at the landing spot; only the capture stun needs help, because it drags the body back to its lock
 * point every tick, so the pull moves that lock point. Never throws.
 * 隙行者共用的「被定住」探针。进门（C10）拒绝被猎人捕兽夹定住、或被 SparkStrength 捕捉装置眩晕的玩家（否则进门等于挣脱）。
 * 魔女集会（D19）会召集这类队友，他们到达落点后仍保持原状态；只有捕捉眩晕需要额外处理——它每 tick 会把本体拉回锁定点，
 * 因此召集时会把锁定点移到落点。从不抛出异常。
 */
public final class RiftwalkerStatusProbes {
    static final String SPARK_STRENGTH_MOD_ID = "sparkstrength";
    static final Identifier CAPTURE_STUN_COMPONENT_ID = Identifier.of(SPARK_STRENGTH_MOD_ID, "engineer_stunned");
    static final String STUNNED_GETTER = "isStunned";
    /** {@code stun(int)}: keeps {@code max(remaining, ticks)} and locks at the current position. / 取最大值并锁定在当前位置。 */
    static final String RELOCK_METHOD = "stun";
    /** Ticks passed to {@code stun(int)}; the max keeps the remaining stun. / 传给 {@code stun(int)} 的刻数；取最大值保留剩余眩晕。 */
    static final int RELOCK_TICKS = 1;

    private static final AtomicBoolean CAPTURE_STUN_DISABLED = new AtomicBoolean();
    private static final AtomicBoolean CAPTURE_RELOCK_DISABLED = new AtomicBoolean();
    private static volatile @Nullable Accessor accessor;

    private RiftwalkerStatusProbes() {
    }

    /**
     * The cached methods of one resolved component class; {@code relock} is null when {@code stun(int)} is missing.
     * 已解析组件类的缓存方法；缺少 {@code stun(int)} 时 {@code relock} 为 null。
     */
    private record Accessor(Class<?> type, Method stunned, @Nullable Method relock) {
    }

    /**
     * SparkWitch's own Hunter trap root ({@code HunterPlayerComponent#isRooted}, the predicate the movement mixin
     * uses). A missing component reads as "not rooted".
     * SparkWitch 自有的猎人捕兽夹定身（{@code HunterPlayerComponent#isRooted}，与移动 mixin 使用的判定相同）。缺少组件时视为未定身。
     */
    public static boolean isHunterRooted(@Nullable PlayerEntity player) {
        return player != null
                && HunterPlayerComponent.KEY.maybeGet(player).map(HunterPlayerComponent::isRooted).orElse(false);
    }

    /**
     * External seam (optional SparkStrength, no compile-time dependency): looks the CCA component up by its id
     * {@code sparkstrength:engineer_stunned} and calls its public no-arg {@code boolean isStunned()} reflectively
     * (pattern of {@code compat/SparkStrengthM67Compat}). Absent SparkStrength or a not-yet-registered component reads
     * as "not stunned"; a changed shape (missing or non-boolean getter, a throwing call) disables the seam for the
     * session and also reads as "not stunned" — the worst case is an entry or pull SparkStrength undoes, never a crash.
     * Disabling logs one warning (N-4), so a SparkStrength change does not fail open unnoticed. Server and client safe.
     * 外部接缝（可选 SparkStrength，无编译期依赖）：按 CCA 组件 id {@code sparkstrength:engineer_stunned} 查找组件，并以反射调用其
     * public 无参 {@code boolean isStunned()}（沿用 {@code compat/SparkStrengthM67Compat} 的模式）。未安装 SparkStrength 或组件尚未
     * 注册时视为「未眩晕」；形状变化（getter 缺失或非 boolean、调用抛出异常）会在本次会话中关闭该接缝，同样视为「未眩晕」——
     * 最坏情况只是一次被 SparkStrength 抵消的进门或召集，绝不会崩溃。关闭时记录一次警告（N-4），SparkStrength 改动后不会悄无声息地
     * 失效放行。服务端与客户端均安全。
     */
    public static boolean isCaptureStunned(@Nullable PlayerEntity player) {
        Component component = captureComponent(player);
        if (component == null) {
            return false;
        }
        Accessor resolved = accessorFor(component.getClass());
        return resolved != null && stunned(resolved, component);
    }

    /**
     * D19 fail-closed guard for Witches' Sabbath: capture-stunned while {@link #relockCaptureStun} cannot move the lock
     * point (public {@code void stun(int)} unresolved, or disabled after a failed call). Such a teammate is skipped,
     * never pulled for the capture to yank back. Not stunned (or the probe disabled) reads as false.
     * D19 魔女集会的失败即关闭防护：处于捕捉眩晕，且 {@link #relockCaptureStun} 无法移动锁定点（public {@code void stun(int)}
     * 无法解析，或调用失败后已关闭）。这样的队友会被跳过，绝不召集后又被捕捉装置拉回。未眩晕（或探针已关闭）时返回 false。
     */
    public static boolean isCaptureStunPinned(@Nullable PlayerEntity player) {
        Component component = captureComponent(player);
        if (component == null) {
            return false;
        }
        Accessor resolved = accessorFor(component.getClass());
        return resolved != null && stunned(resolved, component) && !canRelock(resolved);
    }

    /**
     * D19, server only, right after a Witches' Sabbath teleport: if the player is still capture-stunned, calls
     * SparkStrength's public {@code stun(RELOCK_TICKS)} reflectively, which keeps {@code max(remaining, 1)} ticks and
     * moves the lock point to the player's current (landing) position, so the stun goes on there. A free player is never
     * touched (that call would stun them). A failing call disables the re-lock for the session with one warning, and
     * later captured teammates are then skipped by {@link #isCaptureStunPinned}.
     * D19，仅服务端，在魔女集会传送之后立即调用：若玩家仍处于捕捉眩晕，则以反射调用 SparkStrength 的 public
     * {@code stun(RELOCK_TICKS)}——它保留 {@code max(剩余, 1)} 刻，并把锁定点移到玩家当前（落点）位置，眩晕在落点继续。
     * 绝不触碰未眩晕的玩家（该调用会眩晕他们）。调用失败时在本次会话中关闭重新锁定并记录一次警告，之后被捕捉的队友会由
     * {@link #isCaptureStunPinned} 跳过。
     */
    public static void relockCaptureStun(@Nullable PlayerEntity player) {
        Component component = captureComponent(player);
        if (component == null) {
            return;
        }
        Accessor resolved = accessorFor(component.getClass());
        if (resolved == null || !canRelock(resolved) || !stunned(resolved, component)) {
            return;
        }
        try {
            resolved.relock().invoke(component, RELOCK_TICKS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableCaptureRelock(RELOCK_METHOD + "(int) call failed", failure);
        }
    }

    /** The SparkStrength component, or null (absent, not registered, probe disabled). / SparkStrength 组件，或 null。 */
    private static @Nullable Component captureComponent(@Nullable PlayerEntity player) {
        if (player == null || CAPTURE_STUN_DISABLED.get() || !sparkStrengthLoaded()) {
            return null;
        }
        try {
            ComponentKey<?> key = ComponentRegistry.get(CAPTURE_STUN_COMPONENT_ID);
            return key == null ? null : key.getNullable(player);
        } catch (RuntimeException | LinkageError failure) {
            disableCaptureStun("component lookup failed", failure);
            return null;
        }
    }

    private static boolean stunned(Accessor resolved, Component component) {
        try {
            return Boolean.TRUE.equals(resolved.stunned().invoke(component));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableCaptureStun(STUNNED_GETTER + "() call failed", failure);
            return false;
        }
    }

    private static boolean canRelock(Accessor resolved) {
        return resolved.relock() != null && !CAPTURE_RELOCK_DISABLED.get();
    }

    /**
     * Turns the seam off for the session; only the first caller logs. / 在本次会话中关闭该接缝；只有第一次调用记录日志。
     */
    private static void disableCaptureStun(String why, @Nullable Throwable failure) {
        if (CAPTURE_STUN_DISABLED.compareAndSet(false, true)) {
            SparkWitch.LOGGER.warn("SparkStrength capture-stun probe ({}) disabled: {}; Rift Gate entry and Witches' "
                    + "Sabbath now treat every player as not capture-stunned", CAPTURE_STUN_COMPONENT_ID, why, failure);
        }
    }

    /**
     * Turns only the Sabbath re-lock off (the probe stays on); only the first caller logs.
     * 只关闭魔女集会的重新锁定（探针仍然有效）；只有第一次调用记录日志。
     */
    private static void disableCaptureRelock(String why, @Nullable Throwable failure) {
        if (CAPTURE_RELOCK_DISABLED.compareAndSet(false, true)) {
            SparkWitch.LOGGER.warn("SparkStrength capture-stun re-lock ({}) disabled: {}; Witches' Sabbath now skips "
                    + "capture-stunned teammates", CAPTURE_STUN_COMPONENT_ID, why, failure);
        }
    }

    private static boolean sparkStrengthLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(SPARK_STRENGTH_MOD_ID);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static @Nullable Accessor accessorFor(Class<?> type) {
        Accessor cached = accessor;
        if (cached != null && cached.type() == type) {
            return cached;
        }
        Method stunned;
        try {
            stunned = type.getMethod(STUNNED_GETTER);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableCaptureStun(STUNNED_GETTER + "() lookup failed", failure);
            return null;
        }
        if (stunned.getReturnType() != boolean.class || Modifier.isStatic(stunned.getModifiers())) {
            disableCaptureStun(STUNNED_GETTER + "() is not a boolean instance getter", null);
            return null;
        }
        Accessor resolved = new Accessor(type, stunned, relockFor(type));
        accessor = resolved;
        return resolved;
    }

    /** {@code public void stun(int)} on an instance, else null (re-lock off, one warning). / 实例方法，否则为 null。 */
    private static @Nullable Method relockFor(Class<?> type) {
        try {
            Method relock = type.getMethod(RELOCK_METHOD, int.class);
            if (relock.getReturnType() == void.class && !Modifier.isStatic(relock.getModifiers())) {
                return relock;
            }
            disableCaptureRelock(RELOCK_METHOD + "(int) is not a void instance method", null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disableCaptureRelock(RELOCK_METHOD + "(int) lookup failed", failure);
        }
        return null;
    }
}
