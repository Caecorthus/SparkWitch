package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The crowbar the Fiend Moment grants (C7, C15). It carries a namespaced custom-data marker, never a name, so the end
 * of the moment takes back exactly the granted stacks; bought or found crowbars carry no marker and are untouched.
 * Server only.
 * 魔人时刻授予的撬棍（C7、C15）。它带有命名空间的自定义数据标记（而非名称），因此时刻结束时只收回授予的物品堆；
 * 购买或拾取的撬棍没有标记，不受影响。仅服务端。
 */
final class FiendMomentCrowbar {
    static final String MARKER_KEY = "sparkwitch:fiend_moment_crowbar";

    private FiendMomentCrowbar() {
    }

    static ItemStack create() {
        ItemStack crowbar = new ItemStack(WatheItems.CROWBAR);
        NbtCompound data = new NbtCompound();
        markData(data);
        crowbar.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        return crowbar;
    }

    /**
     * Removes every marked stack the player holds (main, armor, off hand, crafting grid, cursor), as vanilla
     * {@code /clear} does, and returns how many items were removed.
     * 移除玩家持有的所有带标记物品堆（主背包、盔甲、副手、合成格、光标），做法与原版 {@code /clear} 相同，返回移除数量。
     */
    static int takeBack(ServerPlayerEntity player) {
        int removed = player.getInventory().remove(FiendMomentCrowbar::isMarked, Integer.MAX_VALUE,
                player.playerScreenHandler.getCraftingInput());
        if (removed > 0) {
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.onContentChanged(player.getInventory());
        }
        return removed;
    }

    static boolean isMarked(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return stack.isOf(WatheItems.CROWBAR) && data != null && isMarkedData(data.copyNbt());
    }

    // ---- Pure marker / 纯标记 ----

    static void markData(NbtCompound customData) {
        customData.putBoolean(MARKER_KEY, true);
    }

    static boolean isMarkedData(@Nullable NbtCompound customData) {
        return customData != null && customData.getBoolean(MARKER_KEY);
    }
}
