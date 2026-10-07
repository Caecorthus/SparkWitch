package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.item.CocktailItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

public final class WraithConsumableInventoryRules {
    private WraithConsumableInventoryRules() {
    }

    // Wathe cocktails are consumables even without Minecraft's FOOD component.
    // Wathe 鸡尾酒即使没有 Minecraft 的 FOOD 组件也属于消耗品。
    public static boolean isConsumable(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof CocktailItem);
    }

    public static boolean blocksDrop(boolean restrictedWraith, ItemStack stack) {
        return !WraithParticipationRules.mayDropConsumable(restrictedWraith, isConsumable(stack));
    }
}
