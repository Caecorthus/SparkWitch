package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkwitch.compat.recruitment.RecruitmentShopOutputs;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pre-conversion money/inventory snapshot, including cursor and personal crafting inputs.
 * 转职前金币、库存快照，包含鼠标持有物与玩家合成栏输入；不把合成输出重复退款。 */
final class RecruitmentInventorySnapshot {
    private final List<ItemStack> inventory;
    private final List<ItemStack> crafting;
    private final ItemStack cursor;
    private final ScreenHandler screen;
    private final int finalBalance;

    private RecruitmentInventorySnapshot(List<ItemStack> inventory, List<ItemStack> crafting,
                                         ItemStack cursor, ScreenHandler screen, int finalBalance) {
        this.inventory = inventory;
        this.crafting = crafting;
        this.cursor = cursor;
        this.screen = screen;
        this.finalBalance = finalBalance;
    }

    static RecruitmentInventorySnapshot capture(ServerPlayerEntity player, int oldBalance) {
        var prices = RecruitmentShopOutputs.pricesFor(player);
        List<ItemStack> inventory = new ArrayList<>();
        List<ItemStack> crafting = new ArrayList<>();
        Map<Item, Integer> removed = new HashMap<>();
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot).copy();
            inventory.add(stack);
            countRemoved(removed, stack);
        }
        int craftingSlots = player.currentScreenHandler == player.playerScreenHandler ? 4
                : player.currentScreenHandler instanceof CraftingScreenHandler ? 9 : 0;
        for (int slot = 1; slot <= craftingSlots; slot++) {
            ItemStack stack = player.currentScreenHandler.getSlot(slot).getStack().copy();
            crafting.add(stack);
            countRemoved(removed, stack);
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack().copy();
        countRemoved(removed, cursor);
        int refund = GrandWitchRecruitmentRules.refund(removed, prices);
        return new RecruitmentInventorySnapshot(inventory, crafting, cursor, player.currentScreenHandler,
                GrandWitchRecruitmentRules.balanceAfter(oldBalance, refund));
    }

    int finalBalance() { return finalBalance; }

    /** Prevent role-exit screen callbacks from returning or dropping already-valued inputs.
     * 防止身份退出关闭界面时，将已计价输入返还或掉落造成重复退款。 */
    void detachScreenInputs() {
        screen.setCursorStack(ItemStack.EMPTY);
        for (int slot = 0; slot < crafting.size(); slot++) {
            screen.getSlot(slot + 1).setStack(ItemStack.EMPTY);
        }
    }

    void applyRetainedInventory(ServerPlayerEntity player) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            player.getInventory().setStack(slot, retained(stack) ? stack.copy() : ItemStack.EMPTY);
        }
        screen.setCursorStack(ItemStack.EMPTY);
        for (int slot = 0; slot < crafting.size(); slot++) {
            screen.getSlot(slot + 1).setStack(ItemStack.EMPTY);
        }
        player.closeHandledScreen();
        for (ItemStack stack : crafting) {
            if (retained(stack)) player.getInventory().offerOrDrop(stack.copy());
        }
        if (retained(cursor)) player.getInventory().offerOrDrop(cursor.copy());
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void countRemoved(Map<Item, Integer> removed, ItemStack stack) {
        if (!stack.isEmpty() && !retained(stack)) removed.merge(stack.getItem(), stack.getCount(), Math::addExact);
    }

    static boolean retained(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(WatheItems.KEY) || stack.isOf(ModItems.MASTER_KEY)
                || stack.isOf(ModItems.NEUTRAL_MASTER_KEY) || stack.isOf(WatheItems.REVOLVER)
                || stack.isOf(WatheItems.LETTER));
    }
}
