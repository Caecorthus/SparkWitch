package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
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

    /** Only keys, both NoellesRoles master keys, letters and the SparkStrength tablet survive recruitment; every other
     * stack, the revolver included, is cleared and refunded at shop value. Retained stacks are never refunded
     * ({@link #countRemoved}).
     * Cross-mod contract: the tablet is a free identity device SparkStrength issues at round start, not a shop item, so
     * it is kept in place (registry id only, {@link SparkStrengthTabletCompat#isTablet}) and SparkStrength re-resolves
     * its channel to the recruit's new network. SparkStrength grants each eligible player at most one tablet per round
     * and never re-grants a lost one, so wiping it would leave an already-issued recruit without a tablet. A recruit who
     * holds none gets one from SparkStrength's mid-round reconciliation pass after the recruitment; SparkWitch never
     * grants one.
     * 招募只保留钥匙、NoellesRoles 两种万能钥匙、信件和 SparkStrength 平板；其余物品（含左轮手枪）全部清除并按商店售价
     * 折算。被保留的物品从不退款（{@link #countRemoved}）。
     * 跨模组契约：平板是 SparkStrength 开局免费发放的身份设备而非商店商品，因此原地保留（只按注册 id，
     * {@link SparkStrengthTabletCompat#isTablet}），由 SparkStrength 将其频道重新解析到新阵营的网络。SparkStrength
     * 每局最多给每名符合条件的玩家发放一台且不补发丢失的平板，清除会让已领过平板的被招募者失去平板。身上没有平板的
     * 被招募者在招募完成后由 SparkStrength 的局中对账补发；SparkWitch 从不发放平板。 */
    static boolean retained(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(WatheItems.KEY) || stack.isOf(ModItems.MASTER_KEY)
                || stack.isOf(ModItems.NEUTRAL_MASTER_KEY) || stack.isOf(WatheItems.LETTER)
                || SparkStrengthTabletCompat.isTablet(stack));
    }
}
