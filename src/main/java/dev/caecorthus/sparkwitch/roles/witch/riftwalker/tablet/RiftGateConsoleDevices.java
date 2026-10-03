package dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * External seam (both sides): the Rift Gate console's device is the optional SparkStrength tablet, matched by registry id
 * through {@link SparkStrengthTabletCompat} only (no SparkStrength class is named; absent mod → no console, gates still
 * work). Unlike the Seeker console it follows SparkStrength's own hotbar-only rule (slots 0–8), so the "Witch network"
 * re-issue always reaches a tablet SparkStrength accepts.
 * 外部接缝（双端）：裂隙门控制台的设备是可选的 SparkStrength 平板，只通过 {@link SparkStrengthTabletCompat} 按注册 id
 * 识别（不引用任何 SparkStrength 类；未安装时没有控制台，门照常可用）。与搜寻者控制台不同，它沿用 SparkStrength 自己的
 * 仅快捷栏规则（0–8 格），因此「魔女网络」重发的使用一定落在 SparkStrength 认可的平板上。
 */
public final class RiftGateConsoleDevices {
    private RiftGateConsoleDevices() {
    }

    /** A SparkStrength tablet in hotbar slots 0–8. / 快捷栏 0–8 格中有 SparkStrength 平板。 */
    public static boolean hasTabletInHotbar(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < RiftGateConsoleRules.HOTBAR_SIZE; slot++) {
            if (SparkStrengthTabletCompat.isTablet(inventory.getStack(slot))) {
                return true;
            }
        }
        return false;
    }
}
