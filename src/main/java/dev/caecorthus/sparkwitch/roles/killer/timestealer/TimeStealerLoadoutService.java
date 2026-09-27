package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.api.Role;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Clock loadout: grant on role assignment, per-tick restore into the hotbar for a living owner (plus the owner's stamp
 * upkeep), staggered stray sweep for everyone else, and removal on death or reset. Server-authoritative; it never
 * creates item entities and never routes a Clock through the vanilla give/offer paths (which can spill into hidden
 * slots or the floor), so every placement is an explicit slot write.
 * 时钟装备：职业分配时发放；存活持有者每 tick 恢复到快捷栏（并维护其邮票）；其他人错峰清理残留；死亡或重置时移除。
 * 由服务端裁定；从不生成物品实体，也从不经由原版给予/放入路径（可能落入隐藏栏位或地面）放置时钟，每次放置都是显式的栏位写入。
 */
public final class TimeStealerLoadoutService {
    /** Cadence of the stray sweep for non-holders. / 非持有者残留清理的间隔。 */
    static final int STRAY_SWEEP_INTERVAL_TICKS = 20;
    private static final int NO_SLOT = -1;

    private TimeStealerLoadoutService() {
    }

    /**
     * Every RoleAssigned (round start, SparkTraits compensation, Grand Witch recruitment). An exact Time Stealer ends
     * with exactly one Clock in the hotbar; the round-start cooldown (N1) is written only when the role is newly
     * acquired or the round state belongs to another match, so a repeated assignment keeps the cooldown and stamps.
     * Anyone else loses Clocks and stamps and has the round flag cleared.
     * 每次 RoleAssigned（开局、SparkTraits 补偿、大魔女招募）。精确窃时者最终在快捷栏恰好持有一个时钟；开局冷却（N1）
     * 只在新获得该职业或回合状态属于其他对局时写入，因此重复分配保留冷却与邮票。其他玩家失去时钟与邮票，并清除回合标记。
     */
    public static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
        if (!TimeStealerRules.isTimeStealer(role)) {
            removeClocks(player);
            TimeStampInventory.stripAll(player);
            // A later re-assignment to Time Stealer then counts as newly acquired. / 之后再次分配为窃时者即视为新获得。
            state.setClockReadyAt(0L);
            return;
        }
        boolean continuing = continuesRound(state.clockReadyAt(), state.matchId(), TimeStealerMatch.currentId());
        ensureClockInHotbar(player);
        if (!continuing) {
            writeInitialCooldown(player, state);
        }
    }

    /** Called every server tick for every player from {@link TimeStealerPlayerComponent}. / 由组件对每名玩家每 tick 调用。 */
    public static void tick(ServerPlayerEntity player) {
        if (TimeStealerStampService.mayHold(player)) {
            ensureClockInHotbar(player);
            TimeStealerStampService.tickOwner(player);
            return;
        }
        if (Math.floorMod(player.getServerWorld().getTime() + player.getId(), STRAY_SWEEP_INTERVAL_TICKS) == 0) {
            removeClocks(player);
            TimeStampInventory.stripAll(player);
        }
    }

    /** Terminal death: removes the Clock and every stamp. / 终结死亡：移除时钟与所有邮票。 */
    public static void onDeath(ServerPlayerEntity player) {
        removeClocks(player);
        TimeStealerStampService.reset(player);
    }

    /** ResetPlayer and round finalize: removes the Clock and every stamp. / 重置玩家与局末：移除时钟与所有邮票。 */
    public static void reset(ServerPlayerEntity player) {
        removeClocks(player);
        TimeStealerStampService.reset(player);
    }

    /**
     * Same-match re-assignment detection (N1). RoleAssigned carries no previous role and fires before Wathe binds the
     * match id at ON_FINISH_INITIALIZE, so a round-start assignment sees a null match on both sides. The round flag is
     * therefore {@code ClockReadyAt > 0}: ResetPlayer clears it before round-start assignment, a non-Time-Stealer
     * assignment clears it, and every Time Stealer grant writes a positive tick. The match ids must also agree, which
     * rejects state left over from another match (e.g. a player who missed the previous finalize).
     * 同局重复分配检测（N1）。RoleAssigned 不携带先前职业，且早于 Wathe 在 ON_FINISH_INITIALIZE 绑定对局 id，
     * 因此开局分配时两侧对局均为 null。于是以 {@code ClockReadyAt > 0} 作为回合标记：开局分配前 ResetPlayer 会清除它，
     * 非窃时者分配会清除它，而每次窃时者发放都会写入正的 tick。另外双方对局 id 必须一致，以拒绝其他对局遗留的状态
     * （例如错过上一局收尾的玩家）。
     */
    static boolean continuesRound(long clockReadyAt, @Nullable UUID boundMatch, @Nullable UUID currentMatch) {
        return clockReadyAt > 0L && Objects.equals(boundMatch, currentMatch);
    }

    /**
     * Hotbar index for a new Clock: the selected slot when empty (so a refused selected-slot drop puts it back where it
     * was), else the leftmost empty hotbar slot, else -1.
     * 新时钟的快捷栏下标：选中栏位为空时优先（使被拒绝的选中栏位丢弃把时钟放回原位），否则为最左侧空快捷栏位，否则为 -1。
     */
    static int hotbarTarget(int selectedSlot, IntPredicate emptyHotbarSlot) {
        if (PlayerInventory.isValidHotbarIndex(selectedSlot) && emptyHotbarSlot.test(selectedSlot)) {
            return selectedSlot;
        }
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (emptyHotbarSlot.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /**
     * With a full hotbar, the slot whose item is moved out for the Clock: the rightmost hotbar slot that is not selected,
     * so the held item (for example Wathe's psycho bat, which must stay selected) is never displaced.
     * 快捷栏已满时为时钟腾出的栏位：最右侧且非选中的快捷栏位，因此从不移走手持物品（例如必须保持选中的 Wathe 疯魔球棒）。
     */
    static int displacedHotbarSlot(int selectedSlot) {
        int last = PlayerInventory.getHotbarSize() - 1;
        return selectedSlot == last ? last - 1 : last;
    }

    /**
     * Keeps exactly one Clock and forces it into the hotbar: the leftmost hotbar Clock is kept, every other copy (hidden
     * main slots 9-35, armor, offhand, cursor, crafting grid or open container) is removed, and a missing hotbar Clock is
     * recreated in the hotbar. Cheap when nothing is wrong: a slot scan with no writes.
     * 保持恰好一个时钟并强制其位于快捷栏：保留最左侧的快捷栏时钟，移除其他所有副本（隐藏主背包 9-35、盔甲、副手、光标、
     * 合成格或已打开的容器），并在快捷栏缺失时重新创建。一切正常时开销很低：只扫描栏位而不写入。
     */
    private static void ensureClockInHotbar(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int kept = NO_SLOT;
        int vacated = NO_SLOT;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!TimeStealerInventoryRules.isClock(stack)) {
                continue;
            }
            if (kept == NO_SLOT && PlayerInventory.isValidHotbarIndex(slot)) {
                kept = slot;
                if (stack.getCount() > 1) {
                    stack.setCount(1);
                    changed = true;
                }
                continue;
            }
            inventory.setStack(slot, ItemStack.EMPTY);
            if (vacated == NO_SLOT && isHiddenStorageSlot(slot)) {
                vacated = slot;
            }
            changed = true;
        }
        if (TimeStealerInventoryRules.isClock(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && TimeStealerInventoryRules.isClock(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (kept == NO_SLOT) {
            changed |= placeClockInHotbar(inventory, vacated);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /**
     * Puts a fresh Clock into the hotbar. With a full hotbar the displaced item moves into the slot a misplaced Clock
     * just vacated, else the first empty hidden main slot, else an empty offhand; with no room at all nothing is moved
     * or destroyed and the next tick retries.
     * 把新的时钟放入快捷栏。快捷栏已满时，被移出的物品放入刚被错放时钟腾出的栏位，否则放入第一个空的隐藏主背包栏位，
     * 否则放入空副手；完全没有空间时不移动也不销毁任何物品，由下一 tick 重试。
     */
    private static boolean placeClockInHotbar(PlayerInventory inventory, int vacated) {
        Item clock = SparkWitchItems.timeStealerClock();
        int target = hotbarTarget(inventory.selectedSlot, slot -> inventory.getStack(slot).isEmpty());
        if (target == NO_SLOT) {
            target = displacedHotbarSlot(inventory.selectedSlot);
            int destination = vacated != NO_SLOT ? vacated : emptyHiddenStorageSlot(inventory);
            if (destination == NO_SLOT) {
                return false;
            }
            inventory.setStack(destination, inventory.getStack(target));
        }
        inventory.setStack(target, new ItemStack(clock));
        return true;
    }

    private static int emptyHiddenStorageSlot(PlayerInventory inventory) {
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return inventory.getStack(PlayerInventory.OFF_HAND_SLOT).isEmpty() ? PlayerInventory.OFF_HAND_SLOT : NO_SLOT;
    }

    /** Hidden main slots 9-35 or the offhand; armor slots never receive a displaced item. / 隐藏主背包 9-35 或副手；盔甲栏不接收被移出的物品。 */
    private static boolean isHiddenStorageSlot(int slot) {
        return (slot >= PlayerInventory.getHotbarSize() && slot < PlayerInventory.MAIN_SIZE)
                || slot == PlayerInventory.OFF_HAND_SLOT;
    }

    /**
     * Round-start cooldown: the authoritative {@code ClockReadyAt} plus the matching display cooldown, written exactly
     * through the SparkTraits facade (past Traits cooldown modifiers) with a vanilla fallback when Traits is absent or
     * older; never both, so only one cooldown packet is sent.
     * 开局冷却：写入权威的 {@code ClockReadyAt} 及对应的显示冷却；显示冷却经 SparkTraits 门面精确写入（越过 Traits 冷却倍率），
     * Traits 缺失或过旧时回退到原版写入；二者不会同时执行，因此只发送一次冷却数据包。
     */
    private static void writeInitialCooldown(ServerPlayerEntity player, TimeStealerPlayerComponent state) {
        int ticks = TimeStealerRules.CLOCK_INITIAL_COOLDOWN_TICKS;
        state.setClockReadyAt(player.getServerWorld().getTime() + ticks);
        Item clock = SparkWitchItems.timeStealerClock();
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, clock, ticks)) {
            player.getItemCooldownManager().set(clock, ticks);
        }
    }

    /** Removes every Clock from inventory 0..40, the cursor and open handler slots. / 从背包 0..40、光标与已打开界面栏位移除所有时钟。 */
    private static void removeClocks(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (TimeStealerInventoryRules.isClock(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (TimeStealerInventoryRules.isClock(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        // Also covers the crafting grid and any open foreign container. / 同时覆盖合成格与已打开的外部容器。
        for (Slot slot : handler.slots) {
            if (TimeStealerInventoryRules.isClock(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }
}
