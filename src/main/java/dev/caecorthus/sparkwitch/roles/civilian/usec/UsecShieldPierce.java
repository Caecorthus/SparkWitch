package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Stable contract (owner O3, 2026-10-07): AXMC shield piercing. One rifle kill is ONE ordinary
 * {@code killPlayer(victim, true, shooter, wathe:gun_shot)} run inside a scoped pierce budget ({@link #run}): FMJ 2
 * layers, AP 5, +1 on a SparkTraits Heavy Artillery shot. Only the three shield-layer consumers spend it, each through
 * {@link #tryPierce}: the NoellesRoles whiskey / Iron Man BEFORE listener (re-invoked by
 * {@code mixin/usec/NoellesUsecShieldPierceMixin} through {@link #pierceLayers}), the Guardian Angel shield
 * ({@code GuardianAngelFeatureService}), and Wathe psycho armour, Jester-moment armour included
 * ({@code mixin/usec/GameFunctionsUsecPsychoArmourMixin}). A pierced layer is spent as if it had blocked, then the
 * shot goes on; the layer that finds the budget empty blocks as usual. Every other protection (Saint, Judge, the
 * SparkFactionAPI veto, Fiend, Ninja parry, Pig God, Last Stand, Last Escape, Depression, Jester fake death, Tofana)
 * runs exactly once in its native place and is never pierced, because nothing calls {@code killPlayer} again. Only the
 * exact scoped victim, killer and {@code wathe:gun_shot} reason match, so nested kills inside the scope (a Bodyguard
 * sacrifice, a death in AFTER, Tofana retaliation) never spend it. The scope is a server-thread {@link ThreadLocal},
 * restored in {@code finally}. Holds no item, world or registry state, so it is unit-testable.
 * 稳定契约（所有者 O3，2026-10-07）：AXMC 穿盾。一次步枪击杀就是在限定作用域的穿盾预算内（{@link #run}）执行的一次普通
 * {@code killPlayer(victim, true, shooter, wathe:gun_shot)}：FMJ 2 层，AP 5 层，SparkTraits 重炮手射击再 +1。只有三个护盾层
 * 消耗方会通过 {@link #tryPierce} 花费它：NoellesRoles 威士忌 / 铁人 BEFORE 监听（由
 * {@code mixin/usec/NoellesUsecShieldPierceMixin} 经 {@link #pierceLayers} 重复调用）、守护天使护盾
 * （{@code GuardianAngelFeatureService}），以及 Wathe 疯魔护甲，含小丑时刻护甲（{@code mixin/usec/GameFunctionsUsecPsychoArmourMixin}）。
 * 被击穿的一层如同挡下一样被消耗，随后子弹继续；遇到预算已空的那一层照常挡下。其他所有保护（圣徒、法官、SparkFactionAPI 否决、
 * 魔人、忍者格挡、猪神、背水一战、绝处逢生、抑郁、小丑假死、托法娜）都只在原处执行一次且从不被击穿，因为没有任何地方再次调用
 * {@code killPlayer}。只有作用域内确切的受害者、击杀者与 {@code wathe:gun_shot} 死因才匹配，因此作用域内的嵌套击杀（保镖牺牲、
 * AFTER 中的死亡、托法娜反击）从不花费预算。作用域是服务端线程上的 {@link ThreadLocal}，在 {@code finally} 中恢复。不持有任何
 * 物品、世界或注册表状态，可直接单测。
 */
public final class UsecShieldPierce {
    private static final ThreadLocal<Budget> SCOPE = new ThreadLocal<>();

    private UsecShieldPierce() {
    }

    /** Layers one shot may pierce. / 单发可击穿的层数。 */
    public static int budget(UsecAmmoType ammo, boolean heavyArtillery) {
        return Objects.requireNonNull(ammo, "ammo").shieldPierce()
                + (heavyArtillery ? UsecRules.HEAVY_ARTILLERY_SHIELD_PIERCE_BONUS : 0);
    }

    /**
     * Runs {@code kill} (the single AXMC {@code killPlayer}) with {@code budget} pierces for this exact shooter and
     * victim; the previous scope comes back afterwards, even on a throw.
     * 以针对该射手与受害者的 {@code budget} 次穿透执行 {@code kill}（唯一的 AXMC {@code killPlayer}）；之后恢复原作用域，抛出异常时亦然。
     */
    public static void run(UUID shooter, UUID victim, int budget, Runnable kill) {
        Budget previous = SCOPE.get();
        SCOPE.set(new Budget(shooter, victim, budget));
        try {
            kill.run();
        } finally {
            if (previous == null) {
                SCOPE.remove();
            } else {
                SCOPE.set(previous);
            }
        }
    }

    /**
     * Whether this kill is the scoped AXMC shot (exact victim, exact killer, {@code wathe:gun_shot}), budget left or
     * not. / 本次击杀是否为作用域内的 AXMC 射击（确切的受害者、击杀者与 {@code wathe:gun_shot}），不论预算是否剩余。
     */
    public static boolean isScoped(@Nullable PlayerEntity victim, @Nullable PlayerEntity killer,
                                   @Nullable Identifier reason) {
        return victim != null && killer != null && isScoped(victim.getUuid(), killer.getUuid(), reason);
    }

    static boolean isScoped(UUID victim, UUID killer, @Nullable Identifier reason) {
        Budget scope = SCOPE.get();
        return scope != null && scope.matches(victim, killer, reason);
    }

    /**
     * A shield-layer consumer asks this right after spending one layer: true spends one pierce, and the consumer then
     * lets the shot go on instead of blocking it. Never true outside the exact scope or with the budget empty.
     * 护盾层消耗方在消耗一层后立即询问：返回 true 即花费一次穿透，消耗方随后放行子弹而不是挡下。作用域不匹配或预算已空时绝不为 true。
     */
    public static boolean tryPierce(@Nullable PlayerEntity victim, @Nullable PlayerEntity killer,
                                    @Nullable Identifier reason) {
        return victim != null && killer != null && tryPierce(victim.getUuid(), killer.getUuid(), reason);
    }

    static boolean tryPierce(UUID victim, UUID killer, @Nullable Identifier reason) {
        Budget scope = SCOPE.get();
        return scope != null && scope.matches(victim, killer, reason) && scope.trySpend();
    }

    /**
     * Pure re-invocation loop for a listener that spends at most one layer per call and then cancels (the NoellesRoles
     * whiskey / Iron Man listener): call it; while its result is a cancel that spent exactly one layer and a pierce is
     * paid for, call it again. A cancel that spent no layer (a non-shield branch such as Jester stasis or the Jester
     * moment), a non-cancel result, or an empty budget is returned as is. Each extra call costs one pierce, so it ends.
     * 针对每次调用至多消耗一层后取消的监听（NoellesRoles 威士忌 / 铁人监听）的纯重复调用循环：调用它；只要结果是恰好消耗了一层的
     * 取消且穿透已付费，就再次调用。未消耗任何层的取消（非护盾分支，如小丑禁锢或小丑时刻）、非取消结果或预算已空时原样返回。
     * 每多调用一次就花费一次穿透，因此必然结束。
     */
    public static <R> R pierceLayers(Supplier<Layers> layers, Supplier<R> listener, Predicate<R> cancelled,
                                     BooleanSupplier pierce) {
        while (true) {
            Layers before = layers.get();
            R result = listener.get();
            if (!cancelled.test(result) || !before.spentExactlyOne(layers.get()) || !pierce.getAsBoolean()) {
                return result;
            }
        }
    }

    /**
     * NoellesRoles shield layers on the victim: the Iron Man buff and the whiskey stack (amplifier + 1).
     * 受害者身上的 NoellesRoles 护盾层：铁人增益与威士忌层数（等级 + 1）。
     */
    public record Layers(boolean ironMan, int whiskey) {
        /** One layer, and only one, was spent since this snapshot. / 自本快照以来恰好消耗了一层。 */
        public boolean spentExactlyOne(Layers after) {
            boolean ironSpent = ironMan && !after.ironMan();
            int whiskeySpent = whiskey - after.whiskey();
            return ironSpent ? whiskeySpent == 0 : (ironMan == after.ironMan() && whiskeySpent == 1);
        }
    }

    /** One shot's scope. Server thread only. / 单发射击的作用域。仅服务端线程。 */
    static final class Budget {
        private final UUID shooter;
        private final UUID victim;
        private int remaining;

        Budget(UUID shooter, UUID victim, int remaining) {
            this.shooter = Objects.requireNonNull(shooter, "shooter");
            this.victim = Objects.requireNonNull(victim, "victim");
            this.remaining = Math.max(0, remaining);
        }

        boolean matches(UUID victimUuid, UUID killerUuid, @Nullable Identifier reason) {
            return victim.equals(victimUuid) && shooter.equals(killerUuid)
                    && GameConstants.DeathReasons.GUN.equals(reason);
        }

        boolean trySpend() {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }
}
