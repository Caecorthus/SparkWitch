package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.Holdings;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.Kind;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.Move;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.Plan;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.SlotState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Minecraft adapter for physical Time Stamps. The balance is the stamps in the player's own inventory (hotbar, hidden
 * main slots, offhand) plus the open handler's cursor; the owner's client computes the same value from its synced
 * inventory, so no stamp counter is ever synced. Stamps are recognised by item class and their data components are
 * ignored; every written stack is a fresh default stack. Writes go straight to slots and never through the vanilla
 * give, offer, insert or drop helpers, so no write can spill a stamp into the world.
 * 实体时光邮票的 Minecraft 适配层。余额为玩家自身背包（快捷栏、隐藏主背包、副手）加打开界面光标中的邮票数；
 * 拥有者客户端从已同步的背包计算出相同的值，因此从不同步任何邮票计数。按物品类识别邮票并忽略其数据组件；
 * 每次写入都是全新的默认物品堆。写入直接作用于槽位，从不经过原版的给予、提供、插入或丢弃辅助方法，因此任何写入都不会把邮票掉到地上。
 */
public final class TimeStampInventory {
    private TimeStampInventory() {
    }

    /**
     * Side-agnostic stamp balance (server purchases, client price label and HUD); 0 for a null player.
     * 两端通用的邮票余额（服务端购买、客户端价格标签与 HUD）；玩家为 null 时为 0。
     */
    public static int balance(@Nullable PlayerEntity player) {
        return player == null ? 0 : TimeStampLedger.balance(read(player));
    }

    /** Snapshot of PlayerInventory 0..40 plus the current handler's cursor. / 背包 0..40 加当前界面光标的快照。 */
    static Holdings read(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        List<SlotState> states = new ArrayList<>(TimeStampLedger.INVENTORY_SIZE);
        for (int index = 0; index < TimeStampLedger.INVENTORY_SIZE; index++) {
            states.add(index < inventory.size() ? classify(inventory.getStack(index)) : SlotState.EMPTY);
        }
        ScreenHandler handler = player.currentScreenHandler;
        ItemStack cursor = handler == null ? ItemStack.EMPTY : handler.getCursorStack();
        SlotState cursorState = classify(cursor);
        return new Holdings(states, cursorState.isStamps() ? cursorState.count() : 0,
                cursorState.kind() == Kind.OTHER);
    }

    /**
     * Server: applies a ledger plan in order and resyncs the owner. Returns the stamps a positive move could not place
     * (the target changed kind), which callers add to {@code UndeliveredStamps}; plans are applied right after
     * {@link #read}, so this is normally 0.
     * 服务端：按顺序应用账本计划并重新同步给拥有者。返回正向移动未能放下的邮票数（目标格类型已变化），调用方将其计入
     * {@code UndeliveredStamps}；计划总在 {@link #read} 之后立即应用，因此通常为 0。
     */
    static int apply(ServerPlayerEntity player, Plan plan) {
        if (plan.moves().isEmpty()) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int unplaced = 0;
        for (Move move : plan.moves()) {
            if (move.delta() == 0) {
                continue;
            }
            ItemStack current = move.index() == TimeStampLedger.CURSOR
                    ? handler.getCursorStack()
                    : move.index() >= 0 && move.index() < inventory.size() ? inventory.getStack(move.index()) : null;
            if (current == null) {
                unplaced += Math.max(0, move.delta());
                continue;
            }
            boolean stamps = TimeStealerInventoryRules.isStamp(current);
            if (!stamps && !current.isEmpty()) {
                unplaced += Math.max(0, move.delta());
                continue;
            }
            int next = (stamps ? current.getCount() : 0) + move.delta();
            ItemStack replacement = next > 0 ? new ItemStack(SparkWitchItems.timeStamp(), next) : ItemStack.EMPTY;
            if (move.index() == TimeStampLedger.CURSOR) {
                handler.setCursorStack(replacement);
            } else {
                inventory.setStack(move.index(), replacement);
            }
        }
        inventory.markDirty();
        // Also syncs the cursor stack to the owner. / 同时向拥有者同步光标物品。
        handler.sendContentUpdates();
        return unplaced;
    }

    /**
     * Server: removes every stamp from inventory 0..40, the cursor and every slot of the open handler (crafting grid,
     * foreign container); returns the count removed.
     * 服务端：清除背包 0..40、光标以及打开界面所有槽位（合成格、外部容器）中的全部邮票，并返回清除数量。
     */
    public static int stripAll(ServerPlayerEntity player) {
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < inventory.size(); index++) {
            ItemStack stack = inventory.getStack(index);
            if (TimeStealerInventoryRules.isStamp(stack)) {
                removed += stack.getCount();
                inventory.setStack(index, ItemStack.EMPTY);
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (TimeStealerInventoryRules.isStamp(handler.getCursorStack())) {
            removed += handler.getCursorStack().getCount();
            handler.setCursorStack(ItemStack.EMPTY);
        }
        // Handler slots backed by the player inventory are already empty here, so nothing is counted twice.
        // 由玩家背包支撑的界面槽位此时已为空，因此不会重复计数。
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (TimeStealerInventoryRules.isStamp(stack)) {
                removed += stack.getCount();
                slot.setStack(ItemStack.EMPTY);
            }
        }
        if (removed > 0) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
        return removed;
    }

    private static SlotState classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SlotState.EMPTY;
        }
        return TimeStealerInventoryRules.isStamp(stack) ? SlotState.stamps(stack.getCount()) : SlotState.OTHER;
    }
}
