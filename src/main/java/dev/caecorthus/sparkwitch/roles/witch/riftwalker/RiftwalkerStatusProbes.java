package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

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

/**
 * Riftwalker-shared "held in place" probes (C10): a player rooted by a Hunter trap or held by the SparkStrength
 * capture-device stun may neither enter a Rift Gate (entry would be a free escape) nor be pulled by Witches' Sabbath
 * (the holder drags them back every tick). Read-only; never throws.
 * 隙行者共用的「被定住」探针（C10）：被猎人捕兽夹定住、或被 SparkStrength 捕捉装置眩晕的玩家，既不能进入裂隙门
 * （否则进门等于挣脱），也不会被魔女集会召集（定身状态每 tick 会把人拉回去）。只读，从不抛出异常。
 */
public final class RiftwalkerStatusProbes {
    static final String SPARK_STRENGTH_MOD_ID = "sparkstrength";
    static final Identifier CAPTURE_STUN_COMPONENT_ID = Identifier.of(SPARK_STRENGTH_MOD_ID, "engineer_stunned");
    static final String STUNNED_GETTER = "isStunned";

    private static volatile boolean captureStunDisabled;
    private static volatile @Nullable Accessor accessor;

    private RiftwalkerStatusProbes() {
    }

    /** The cached getter of one resolved component class. / 已解析组件类的缓存 getter。 */
    private record Accessor(Class<?> type, Method stunned) {
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
     * Server and client safe.
     * 外部接缝（可选 SparkStrength，无编译期依赖）：按 CCA 组件 id {@code sparkstrength:engineer_stunned} 查找组件，并以反射调用其
     * public 无参 {@code boolean isStunned()}（沿用 {@code compat/SparkStrengthM67Compat} 的模式）。未安装 SparkStrength 或组件尚未
     * 注册时视为「未眩晕」；形状变化（getter 缺失或非 boolean、调用抛出异常）会在本次会话中关闭该接缝，同样视为「未眩晕」——
     * 最坏情况只是一次被 SparkStrength 抵消的进门或召集，绝不会崩溃。服务端与客户端均安全。
     */
    public static boolean isCaptureStunned(@Nullable PlayerEntity player) {
        if (player == null || captureStunDisabled || !sparkStrengthLoaded()) {
            return false;
        }
        Component component;
        try {
            ComponentKey<?> key = ComponentRegistry.get(CAPTURE_STUN_COMPONENT_ID);
            component = key == null ? null : key.getNullable(player);
        } catch (RuntimeException | LinkageError failure) {
            captureStunDisabled = true;
            return false;
        }
        if (component == null) {
            return false;
        }
        Accessor resolved = accessorFor(component.getClass());
        if (resolved == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(resolved.stunned().invoke(component));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            captureStunDisabled = true;
            return false;
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
        try {
            Method stunned = type.getMethod(STUNNED_GETTER);
            if (stunned.getReturnType() != boolean.class || Modifier.isStatic(stunned.getModifiers())) {
                captureStunDisabled = true;
                return null;
            }
            Accessor resolved = new Accessor(type, stunned);
            accessor = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            captureStunDisabled = true;
            return null;
        }
    }
}
