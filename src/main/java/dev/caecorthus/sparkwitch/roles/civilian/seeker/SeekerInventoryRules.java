package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Seeker device items cannot be dropped, thrown, duplicated or moved into another inventory; while viewing, every drop
 * and slot click is denied.
 * TODO(WP-07): implement. / 待 WP-07 实现。
 * 搜寻者设备物品不能丢弃、投掷、复制，也不能移入他人槽位；观看期间禁止一切丢弃与槽位点击。
 */
public final class SeekerInventoryRules {
    private SeekerInventoryRules() {
    }

    public static boolean isProtectedItem(@Nullable ItemStack stack) {
        return false;
    }
}
