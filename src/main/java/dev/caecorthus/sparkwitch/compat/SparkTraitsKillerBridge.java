package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Optional public-facade queries; older/absent Traits adds no restrictions.
 * 可选公共门面查询；旧版或缺失 Traits 时不附加限制。 */
public final class SparkTraitsKillerBridge {
    private static final String API = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Method ACTIVE = resolve("isLastEscapeActive", PlayerEntity.class);
    private static final Method BLOCKED = resolve("isKillerInteractionBlocked", PlayerEntity.class);
    private static final Method FORCED = resolve("getForcedMeleeCooldownTicks", PlayerEntity.class, ItemStack.class);
    private static final Method MELEE = resolve("shouldCancelMeleeAttack", ServerPlayerEntity.class,
            ServerPlayerEntity.class, ItemStack.class);
    private static final Method DESATURATION = resolve("getLastEscapeDesaturation", PlayerEntity.class);
    private static final Method ROLE_SKILL = resolve("isRoleSkillBlocked", PlayerEntity.class);
    private static final Method TERMINAL = resolve("registerTerminalDeathReason", Identifier.class);
    private static final Method EXACT_COOLDOWN = resolve("setExactItemCooldownRemaining", ServerPlayerEntity.class,
            Item.class, int.class);
    private static final Method NON_FINAL_KILL = resolve("isNonFinalKillPending", ServerPlayerEntity.class,
            ServerPlayerEntity.class);
    private static final Method NON_FINAL_COOLDOWN = resolve("getNonFinalKillCooldownTicks", int.class);
    private static final Method NON_FINAL_WEAPON = resolve("runWithNonFinalKillWeapon", Item.class, Runnable.class);

    private SparkTraitsKillerBridge() {
    }

    public static boolean isLastEscapeActive(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invoke(ACTIVE, player));
    }

    public static boolean isKillerInteractionBlocked(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invoke(BLOCKED, player));
    }

    public static int getForcedMeleeCooldownTicks(PlayerEntity player, ItemStack weapon) {
        Object result = player == null ? null : invoke(FORCED, player, weapon);
        return result instanceof Integer ticks ? Math.max(0, ticks) : 0;
    }

    /** New action gate, not a kill-source filter: emitted projectiles and marks keep resolving.
     * 仅拦截新动作，不过滤击杀来源；已发出的投射物与标记继续结算。 */
    public static boolean blocksWeaponAction(PlayerEntity player, ItemStack weapon) {
        return isKillerInteractionBlocked(player) || getForcedMeleeCooldownTicks(player, weapon) > 0;
    }

    /** Call only after target/weapon eligibility, immediately before real melee side effects.
     * 仅在目标及武器资格验证后、实际近战副作用前调用。 */
    public static boolean shouldCancelMeleeAttack(ServerPlayerEntity attacker, ServerPlayerEntity victim,
                                                 ItemStack weapon) {
        return Boolean.TRUE.equals(invoke(MELEE, attacker, victim, weapon));
    }

    public static float getLastEscapeDesaturation(PlayerEntity player) {
        Object result = player == null ? null : invoke(DESATURATION, player);
        return result instanceof Float factor && Float.isFinite(factor)
                ? Math.clamp(factor, 0.0F, 1.0F) : 0.0F;
    }

    /** Last Escape or silenced effective killer; SparkWitch packets must ask themselves.
     * 脱险或被沉默的有效杀手；SparkWitch 自有数据包必须自行查询。 */
    public static boolean isRoleSkillBlocked(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invoke(ROLE_SKILL, player));
    }

    /** Makes a reason skip Traits revive/counter hooks; returns false when Traits is absent or older.
     * 使该死亡原因跳过 Traits 复活/反击钩子；Traits 缺失或过旧时返回 false。 */
    public static boolean registerTerminalDeathReason(Identifier reason) {
        return reason != null && invokeVoid(TERMINAL, reason);
    }

    /** Writes an exact cooldown past Traits modifiers, performing the vanilla set itself; on false the
     * caller must fall back to {@code ItemCooldownManager.set}.
     * 越过 Traits 冷却倍率写入精确冷却（其内部自行完成原版写入）；返回 false 时调用方需回退到原版冷却写入。 */
    public static boolean setExactItemCooldownRemaining(ServerPlayerEntity player, Item item, int ticks) {
        return player != null && item != null && invokeVoid(EXACT_COOLDOWN, player, item, Math.max(0, ticks));
    }

    /** Whether Traits turned this attacker's hit into a non-final kill (e.g. a Depression fake death);
     * older/absent Traits never does.
     * Traits 是否把该攻击者的这一击变为非最终击杀（如抑郁假死）；旧版或缺失 Traits 时恒为 false。 */
    public static boolean isNonFinalKillPending(ServerPlayerEntity victim, ServerPlayerEntity attacker) {
        return victim != null && attacker != null && Boolean.TRUE.equals(invoke(NON_FINAL_KILL, victim, attacker));
    }

    /** Cooldown a weapon keeps after a non-final kill; never longer than the original, which is also the
     * fallback when Traits is absent or older.
     * 非最终击杀后武器保留的冷却；不会超过原冷却，Traits 缺失或过旧时即为原冷却。 */
    public static int nonFinalKillCooldownTicks(int originalTicks) {
        int original = Math.max(0, originalTicks);
        return invoke(NON_FINAL_COOLDOWN, original) instanceof Integer ticks
                ? Math.min(original, Math.max(0, ticks)) : original;
    }

    /** Runs a kill that happens away from the attacker's hands, naming the weapon Traits should discount if the
     * kill turns non-final. The kill always runs exactly once, whatever Traits does.
     * 执行不在攻击者手中发生的击杀，并告知 Traits 若成为非最终击杀应缩短哪件武器的冷却；无论 Traits 如何，击杀恰好执行一次。 */
    public static void runWithNonFinalKillWeapon(Item weapon, Runnable kill) {
        if (NON_FINAL_WEAPON == null || weapon == null) {
            kill.run();
            return;
        }
        boolean[] ran = {false};
        Runnable once = () -> {
            ran[0] = true;
            kill.run();
        };
        try {
            NON_FINAL_WEAPON.invoke(null, weapon, once);
            if (!ran[0]) {
                kill.run();
            }
        } catch (InvocationTargetException exception) {
            if (!ran[0]) {
                kill.run();
                return;
            }
            // The kill itself failed: surface it exactly as a direct call would.
            // 击杀本身抛出异常：与直接调用一样原样抛出。
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            if (!ran[0]) {
                kill.run();
            }
        }
    }

    private static Method resolve(String name, Class<?>... parameters) {
        try {
            return Class.forName(API, false, SparkTraitsKillerBridge.class.getClassLoader())
                    .getMethod(name, parameters);
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static boolean invokeVoid(Method method, Object... args) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, args);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static Object invoke(Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
