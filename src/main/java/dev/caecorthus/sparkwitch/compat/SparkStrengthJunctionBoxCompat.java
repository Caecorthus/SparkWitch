package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Optional SparkStrength junction-box seam (owner decision 2026-10-21): a weapon hit on a saboteur junction box
 * ({@code sparkstrength:junction_box}). SparkWitch never compiles against SparkStrength, so this is the only SparkWitch
 * class that names {@code annina.sparkstrength.sabotage.JunctionBoxService#damageByWeapon(ServerPlayerEntity,
 * BlockPos, int)}, reached by reflection on the server thread. SparkStrength owns every sabotage rule (who may pry,
 * closed and wired box, sabotage still locked, map ready) and checks them silently; reaching 0 durability opens the
 * box like a finished pry, credited to the attacker. Absent, older (no such method) or failing SparkStrength means the
 * hit does nothing to the box.
 * 可选的 SparkStrength 接线箱接缝（所有者 2026-10-21 决定）：武器命中破坏者接线箱（{@code sparkstrength:junction_box}）。
 * SparkWitch 从不针对 SparkStrength 编译，因此只有本类会点名
 * {@code annina.sparkstrength.sabotage.JunctionBoxService#damageByWeapon(ServerPlayerEntity, BlockPos, int)}，
 * 并在服务端线程经反射调用。所有破坏规则（谁可撬箱、箱子关闭且已接线、破坏仍处于锁定、地图就绪）由 SparkStrength
 * 自行静默校验；耐久降至 0 时如同撬箱完成般打开箱子，并记在攻击者名下。SparkStrength 缺失、过旧（无此方法）或调用失败时，
 * 这次命中对箱子毫无作用。
 */
public final class SparkStrengthJunctionBoxCompat {
    private static final String MOD_ID = "sparkstrength";
    /**
     * The block a caller filters on before any reflection; public because the callers live outside this package.
     * 调用方在反射之前用来过滤的方块 id；调用方不在本包内，因此为 public。
     */
    public static final Identifier JUNCTION_BOX_ID = Identifier.of(MOD_ID, "junction_box");
    /** Frozen SparkStrength seam. / 冻结的 SparkStrength 接缝。 */
    static final String JUNCTION_BOX_SERVICE = "annina.sparkstrength.sabotage.JunctionBoxService";
    static final String DAMAGE_BY_WEAPON = "damageByWeapon";
    /**
     * Resolved once, after the mod-loaded guard; SparkStrength's classes never change at runtime, so a failed lookup is
     * latched (the hit does nothing). Ungated because {@link #damage} keeps its own per-call loader guard, outside any
     * catch, exactly as before; no return-type check, so any Boolean-valued result is accepted as before.
     * 在模组加载守卫之后解析一次；SparkStrength 的类在运行期不会改变，因此查找失败会被锁存（命中无效）。使用 ungated，
     * 因为 {@link #damage} 仍按原样在任何 catch 之外逐次执行自己的加载器守卫；不检查返回类型，因此与此前一样接受任何
     * Boolean 结果。
     */
    private static final OptionalStaticMethod DAMAGE_BY_WEAPON_METHOD = OptionalStaticMethod.ungated(
            JUNCTION_BOX_SERVICE, DAMAGE_BY_WEAPON, ServerPlayerEntity.class, BlockPos.class, int.class)
            .initializingClass();

    private SparkStrengthJunctionBoxCompat() {
    }

    /**
     * Deals {@code damage} durability (SparkStrength's unit: hundredths of a percent, 10_000 = 100%) to the junction
     * box at {@code pos} as {@code attacker}'s weapon hit. Server thread only. True iff SparkStrength applied it.
     * 以 {@code attacker} 的武器命中对 {@code pos} 处接线箱造成 {@code damage} 点耐久伤害（SparkStrength 的单位：
     * 万分之一，10_000 = 100%）。仅限服务端线程。仅当 SparkStrength 实际扣除时返回 true。
     */
    public static boolean damage(ServerPlayerEntity attacker, BlockPos pos, int damage) {
        if (attacker == null || pos == null || damage <= 0 || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        // Fail closed: the shot leaves the box untouched. / 失败即关闭：这一枪不影响箱子。
        return DAMAGE_BY_WEAPON_METHOD.callBoolean(false, false, false, attacker, pos, damage);
    }
}
