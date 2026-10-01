package dev.caecorthus.sparkwitch.roles.killer.hunter;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Identifies the Hunter loadout for the death-drop mixin and the post-death cleanup.
 * The items are identified by class, so these rules stay safe before registry lookups.
 * 为死亡掉落 mixin 与死后清理识别猎人装备；按物品类识别，因此不依赖注册表获取。
 */
public final class HunterInventoryRules {
    private HunterInventoryRules() {
    }

    /** Shotgun, shells, and traps. / 猎枪、弹药与捕兽夹。 */
    public static boolean isHunterLoadout(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof DoubleBarrelShotgunItem
                || item instanceof DoubleBarrelShellItem
                || item instanceof HunterTrapItem;
    }
}
