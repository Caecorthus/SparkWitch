package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * External seam (hard dependency, pinned NoellesRoles 1.7.6): the Seeker's Taotie reads. Callers outside the Taotie
 * package use only {@link #isSwallowed} and {@link #isTaotie}.
 * TODO(WP-06): implement with the direct NoellesRoles API. / 待 WP-06 使用 NoellesRoles 直接 API 实现。
 * 外部接缝（硬依赖，固定 NoellesRoles 1.7.6）：搜寻者对饕餮的读取。饕餮包之外的调用方只使用 {@link #isSwallowed} 与 {@link #isTaotie}。
 */
public final class NoellesTaotieSeekerBridge {
    private NoellesTaotieSeekerBridge() {
    }

    public static boolean isSwallowed(@Nullable PlayerEntity player) {
        return false;
    }

    public static boolean isTaotie(@Nullable PlayerEntity player) {
        return false;
    }

    public static int swallowCooldown(PlayerEntity taotie) {
        return 0;
    }

    /** Writes NoellesRoles' dynamic swallow cooldown. / 写入 NoellesRoles 的动态吞噬冷却。 */
    public static void consumeSwallowCooldown(PlayerEntity taotie) {
    }
}
