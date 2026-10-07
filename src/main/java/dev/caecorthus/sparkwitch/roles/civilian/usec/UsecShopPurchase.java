package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Buy handler for the loose .338 rounds only (magazines and the suppressor keep Wathe's default {@code onBuy}). A bought
 * round first tops up a same-item stack with room in a shown slot: the hotbar 0-8, then SparkFactionAPI's second row
 * 27-35 when {@link SparkFactionSecondRowCompat#isShown()}. Hidden storage is never topped up, because the default path
 * never places a purchase there and the player could not reach it. Otherwise it delegates to {@code fallback}, a
 * handler-less entry whose default {@code onBuy} carries SparkFactionAPI's second-row wrapper, so "nothing fits"
 * still returns false and Wathe charges nothing. Server only (Wathe's {@code tryBuy}).
 * 仅用于散装 .338 子弹的购买处理（弹匣与消音器保留 Wathe 默认 {@code onBuy}）。买到的子弹先补进显示栏位中有空余的同种物品堆：
 * 快捷栏 0-8，然后在 {@link SparkFactionSecondRowCompat#isShown()} 时为 SparkFactionAPI 第二行 27-35。从不补进隐藏栏位，
 * 因为默认路径从不把购买物放在那里，玩家也拿不到。否则交给 {@code fallback}：一个不带处理器的条目，其默认 {@code onBuy}
 * 带有 SparkFactionAPI 的第二行包装，因此“放不下”仍返回 false，Wathe 不扣钱。仅服务端（Wathe 的 {@code tryBuy}）。
 */
public final class UsecShopPurchase {
    private UsecShopPurchase() {
    }

    /**
     * Tops up the first shown same-item stack that can take the whole purchase, else runs the default insertion.
     * A partial top-up is never made, so a failed fallback can never leave unpaid rounds behind.
     * 补进第一个能容纳整次购买的显示中同种物品堆，否则执行默认插入。从不部分补入，因此默认插入失败时不会留下未付款的子弹。
     */
    public static boolean buyRound(PlayerEntity player, ShopEntry fallback) {
        ItemStack bought = fallback.getActualStack();
        PlayerInventory inventory = player.getInventory();
        int slot = firstShownStackWithRoom(index -> fitsWhole(inventory, inventory.getStack(index), bought),
                SparkFactionSecondRowCompat.isShown());
        if (slot < 0) {
            return fallback.onBuy(player);
        }
        inventory.getStack(slot).increment(bought.getCount());
        inventory.markDirty();
        return true;
    }

    /**
     * Shown slots in top-up order: hotbar 0-8, then the second row 27-35 only when it is shown; never 9-26, and never
     * 27-35 while it is hidden.
     * 按补入顺序排列的显示栏位：快捷栏 0-8，然后仅在第二行显示时为 27-35；从不包含 9-26，第二行隐藏时也不包含 27-35。
     */
    static List<Integer> shownTopUpSlots(boolean secondRowShown) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            slots.add(slot);
        }
        if (secondRowShown) {
            for (int slot = SparkFactionSecondRowCompat.SECOND_ROW_START;
                 slot < SparkFactionSecondRowCompat.SECOND_ROW_END; slot++) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** First shown slot whose stack can take the purchase, or -1. / 第一个能容纳本次购买的显示栏位，没有则为 -1。 */
    static int firstShownStackWithRoom(IntPredicate fitsWhole, boolean secondRowShown) {
        for (int slot : shownTopUpSlots(secondRowShown)) {
            if (fitsWhole.test(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /** Whether {@code incoming} more items fit on a stack of {@code count} with cap {@code max}. / 是否还能放下。 */
    static boolean hasRoomFor(int count, int max, int incoming) {
        return count > 0 && incoming > 0 && count <= max - incoming;
    }

    private static boolean fitsWhole(PlayerInventory inventory, ItemStack target, ItemStack bought) {
        return !target.isEmpty() && ItemStack.areItemsAndComponentsEqual(target, bought)
                && hasRoomFor(target.getCount(), Math.min(target.getMaxCount(), inventory.getMaxCount(target)),
                bought.getCount());
    }
}
