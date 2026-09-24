package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.item.CocktailItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

public final class WraithConsumableInventoryRules {
    private WraithConsumableInventoryRules() {
    }

    public static boolean isConsumable(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof CocktailItem);
    }

    public static boolean blocksDrop(boolean restrictedWraith, ItemStack stack) {
        return !WraithParticipationRules.mayDropConsumable(restrictedWraith, isConsumable(stack));
    }
}
