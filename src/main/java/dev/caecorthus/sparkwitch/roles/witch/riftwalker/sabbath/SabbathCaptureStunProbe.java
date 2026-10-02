package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

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
 * External seam (optional SparkStrength, no compile-time dependency): whether a player is held by the SparkStrength
 * capture-device stun, which teleports its victim back to the lock point every tick and would undo a Sabbath pull.
 * Looks the CCA component up by its id {@code sparkstrength:engineer_stunned} and calls its public no-arg
 * {@code boolean isStunned()} reflectively (pattern of {@code compat/SparkStrengthM67Compat}). Absent SparkStrength or a
 * not-yet-registered component reads as "not stunned"; a changed shape (missing or non-boolean getter, a throwing
 * call) disables the seam for the session and also reads as "not stunned" — the worst case is a pull that
 * SparkStrength undoes, never a crash or a skill that can no longer find anyone. Server and client safe.
 * 外部接缝（可选 SparkStrength，无编译期依赖）：玩家是否被 SparkStrength 捕捉装置定身——它每 tick 把受害者拉回锁定点，
 * 会抵消魔女集会的召集。按 CCA 组件 id {@code sparkstrength:engineer_stunned} 查找组件，并以反射调用其 public 无参
 * {@code boolean isStunned()}（沿用 {@code compat/SparkStrengthM67Compat} 的模式）。未安装 SparkStrength 或组件尚未注册时
 * 视为「未定身」；形状变化（getter 缺失或非 boolean、调用抛出异常）会在本次会话中关闭该接缝，同样视为「未定身」——
 * 最坏情况只是一次被 SparkStrength 抵消的召集，绝不会崩溃，也不会让技能再也找不到任何人。服务端与客户端均安全。
 */
final class SabbathCaptureStunProbe {
    static final String MOD_ID = "sparkstrength";
    static final Identifier COMPONENT_ID = Identifier.of(MOD_ID, "engineer_stunned");
    static final String STUNNED_GETTER = "isStunned";

    private static volatile boolean disabled;
    private static volatile @Nullable Accessor accessor;

    private SabbathCaptureStunProbe() {
    }

    /** The cached getter of one resolved component class. / 已解析组件类的缓存 getter。 */
    private record Accessor(Class<?> type, Method stunned) {
    }

    static boolean isStunned(@Nullable PlayerEntity player) {
        if (player == null || disabled || !loaded()) {
            return false;
        }
        Component component;
        try {
            ComponentKey<?> key = ComponentRegistry.get(COMPONENT_ID);
            component = key == null ? null : key.getNullable(player);
        } catch (RuntimeException | LinkageError failure) {
            disabled = true;
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
            disabled = true;
            return false;
        }
    }

    private static boolean loaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(MOD_ID);
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
                disabled = true;
                return null;
            }
            Accessor resolved = new Accessor(type, stunned);
            accessor = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disabled = true;
            return null;
        }
    }
}
