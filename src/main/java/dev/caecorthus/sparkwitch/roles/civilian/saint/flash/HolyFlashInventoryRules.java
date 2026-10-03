package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Identifies Holy Flashes for the carry limit, the death-drop mixin and the post-death cleanup. The item is
 * identified by its class, so these rules stay safe before registry lookups.
 * 为携带上限、死亡掉落 mixin 与死后清理识别圣光弹；按物品类识别，因此不依赖注册表获取。
 */
public final class HolyFlashInventoryRules {
    private HolyFlashInventoryRules() {
    }

    public static boolean isHolyFlash(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof HolyFlashItem;
    }

    /** Holy Flashes never enter Wathe's death-drop loop. / 圣光弹永不进入 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isHolyFlash(stack);
    }

    /**
     * Total Holy Flashes in the main inventory, hotbar, offhand, the 2×2 crafting grid and the cursor stack (armor
     * slots never hold one). The cursor and grid count because Wathe's shop accepts clicks while a stack is held,
     * and closing the screen returns that stack to the inventory.
     * 主背包、快捷栏、副手、2×2 合成格与鼠标光标上的圣光弹总数（护甲栏不会放置圣光弹）。计入光标与合成格，
     * 是因为 Wathe 商店在光标拿着物品时仍接受点击，关闭界面后该物品会回到背包。
     */
    public static int carried(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (ItemStack stack : inventory.main) {
            if (isHolyFlash(stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offHand) {
            if (isHolyFlash(stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.playerScreenHandler.getCraftingInput().getHeldStacks()) {
            if (isHolyFlash(stack)) {
                count += stack.getCount();
            }
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (isHolyFlash(cursor)) {
            count += cursor.getCount();
        }
        return count;
    }

    /** Whether one more purchase would exceed the carry limit. / 再买一个是否会超过携带上限。 */
    public static boolean atCarryLimit(int carried) {
        return carried >= HolyFlashRules.CARRY_LIMIT;
    }
}
