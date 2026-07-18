package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Namespaced custom-data marker used only to bridge Wathe's missing cocktail poison call. */
public final class PoisonAppleDrinkMarker {
    private static final String MARKER_KEY = "sparkwitch:poison_apple_drink";

    private PoisonAppleDrinkMarker() {
    }

    public static boolean isMarked(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.copyNbt().getBoolean(MARKER_KEY);
    }

    public static void mark(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        nbt.putBoolean(MARKER_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static void clear(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        nbt.remove(MARKER_KEY);
        if (nbt.isEmpty()) {
            stack.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? new NbtCompound() : customData.copyNbt();
    }
}
