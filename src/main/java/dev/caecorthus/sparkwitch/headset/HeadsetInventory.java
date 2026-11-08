package dev.caecorthus.sparkwitch.headset;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * The one-stack {@link Inventory} behind {@link HeadsetSlot}: a live view of {@link HeadsetSlots} for its player, so
 * the component stays the only storage and every write goes through the facade (which syncs on the server). It is
 * not a {@code PlayerInventory}, so SparkFactionAPI's two-row rules, Wathe's clears and every 0..40 loop ignore it.
 * Removing builds new stacks instead of splitting the stored one, so emptying the slot always replaces the stored
 * stack (and syncs) rather than leaving a zero-count stack behind.
 * {@link HeadsetSlot} 背后的单格 {@link Inventory}：对其玩家 {@link HeadsetSlots} 的实时视图，组件仍是唯一存储，每次写入都经由
 * 门面（服务端会同步）。它不是 {@code PlayerInventory}，因此 SparkFactionAPI 两行规则、Wathe 的清空与所有 0..40 循环都忽略它。
 * 取出时新建物品堆而不拆分已存的那一个，因此清空栏位总会替换已存物品堆（并同步），不会留下数量为 0 的物品堆。
 */
public final class HeadsetInventory implements Inventory {
    private final PlayerEntity player;

    public HeadsetInventory(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity player() {
        return player;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return HeadsetSlots.get(player).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? HeadsetSlots.get(player) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stored = getStack(slot);
        if (stored.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, stored.getCount());
        ItemStack removed = stored.copyWithCount(taken);
        HeadsetSlots.set(player, stored.getCount() > taken ? stored.copyWithCount(stored.getCount() - taken) : null);
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack stored = getStack(slot);
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        HeadsetSlots.set(player, null);
        return stored;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            HeadsetSlots.set(player, stack);
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 && HeadsetSlots.accepts(stack);
    }

    @Override
    public void markDirty() {
        // The component syncs on every write; nothing is cached here. / 组件每次写入都会同步；此处无缓存。
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player == this.player;
    }

    @Override
    public void clear() {
        HeadsetSlots.set(player, null);
    }
}
