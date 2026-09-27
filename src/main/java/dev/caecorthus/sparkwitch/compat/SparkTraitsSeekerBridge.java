package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * External seam: the Seeker's own optional SparkTraits reads through the public {@code SparkTraitsApi} facade only.
 * Absent SparkTraits: not pending, not impostor. Present but a method missing or failing: not pending; impostor
 * query reads as unknown (true).
 * TODO(WP-02): implement the cached reflective lookups. / 待 WP-02 实现带缓存的反射查询。
 * 外部接缝：搜寻者自有的可选 SparkTraits 读取，仅经由公共门面 {@code SparkTraitsApi}。
 * 未安装：非待定、非内鬼。已安装但方法缺失或失败：非待定；内鬼查询视为未知（true）。
 */
public final class SparkTraitsSeekerBridge {
    private SparkTraitsSeekerBridge() {
    }

    public static boolean isLastStandPending(@Nullable PlayerEntity player) {
        return false;
    }

    public static boolean isImpostorOrUnknown(@Nullable PlayerEntity player) {
        return false;
    }
}
