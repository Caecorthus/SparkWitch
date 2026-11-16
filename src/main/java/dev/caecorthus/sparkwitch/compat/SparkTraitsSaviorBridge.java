package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for the Savior pistol. Reflects only the public facade
 * {@code SparkTraitsApi.getServerGunTargetRange(PlayerEntity, Item, double)}: the distance within which the server
 * should accept a player target for this shooter and gun. SparkTraits already widens the client pick through
 * {@code RevolverItem.getGunTarget} (a sneaking Niko holder: 300 blocks), so without this query the server would turn
 * every such shot past the base tolerance into a miss. The answer can only widen the base range, up to 300 blocks; an
 * absent, older or failing provider means the base range.
 * 救世主手枪的可选 SparkTraits 接缝。仅反射公共门面
 * {@code SparkTraitsApi.getServerGunTargetRange(PlayerEntity, Item, double)}：服务端对该射手与该枪接受玩家目标的距离。
 * SparkTraits 已通过 {@code RevolverItem.getGunTarget} 放宽客户端选靶（潜行的 Niko 持有者为 300 格），若不查询，服务端会把
 * 超出基础容差的这类射击全部判为未命中。返回值只能放宽基础距离，最多 300 格；提供方缺失、过旧或出错时使用基础距离。
 */
public final class SparkTraitsSaviorBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    /** Mirrors SparkTraits' Niko gun range; kept local so a wrong provider value cannot remove the cap.
     * 与 SparkTraits 的 Niko 枪械射程一致；在此本地保存，使提供方的错误返回值无法取消上限。 */
    private static final double MAX_RANGE = 300.0;

    private static final OptionalStaticMethod GUN_RANGE = OptionalStaticMethod
            .of(MOD_ID, API_CLASS, "getServerGunTargetRange", PlayerEntity.class, Item.class, double.class)
            .returning(double.class).initializingClass();

    private SparkTraitsSaviorBridge() {
    }

    /**
     * Server only: the player-target distance limit for {@code shooter} firing {@code pistol}, never below
     * {@code baseRange}. Any absence or failure is {@code baseRange}.
     * 仅服务端：{@code shooter} 用 {@code pistol} 开枪时玩家目标的距离上限，不低于 {@code baseRange}。任何缺失或失败均为
     * {@code baseRange}。
     */
    public static double pistolServerRange(@Nullable PlayerEntity shooter, @Nullable Item pistol, double baseRange) {
        return shooter == null || pistol == null
                ? baseRange
                : widenOnly(GUN_RANGE.callOrNull(shooter, pistol, baseRange), baseRange);
    }

    /** A non-double or non-finite value fails closed to the base range; a finite one is held in [base, 300].
     * 非 double 或非有限值回退为基础距离；有限值限制在 [基础距离, 300]。 */
    static double widenOnly(@Nullable Object raw, double baseRange) {
        if (!(raw instanceof Double value) || !Double.isFinite(value)) {
            return baseRange;
        }
        return Math.max(baseRange, Math.min(MAX_RANGE, value));
    }
}
