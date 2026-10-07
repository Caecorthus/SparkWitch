package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import net.minecraft.entity.player.PlayerInventory;

/**
 * Pure decisions of the Shriek Gun reconcile: who is swept, which copy (if any) survives, whether a fresh gun is
 * placed, the sweep cadence, and where a gun goes when the hotbar is full. The server executor lives in
 * {@link AbyssListenerLoadout}.
 * 啸音铳校正的纯判定：校正谁、保留哪一份（如有）、是否放入新枪、清理节奏，以及快捷栏已满时枪放在哪里。
 * 服务端执行位于 {@link AbyssListenerLoadout}。
 */
final class AbyssListenerGunSweep {
    /** Entitlement sweep cadence, staggered by entity id. / 资格清理间隔，按实体 id 错峰。 */
    static final int INTERVAL_TICKS = 20;
    static final int NO_COPY = -1;
    static final int NO_SLOT = -1;

    private AbyssListenerGunSweep() {
    }

    /**
     * Where one found gun copy sits. HOTBAR is a visible, usable slot; CURSOR is a gun mid-move inside the holder's
     * own slots (the slot guard still allows PICKUP there); STRAY is anywhere else (main slots 9-35, including the
     * second row 27-35 that SparkFactionAPI 0.1.5.13+ shows above the hotbar, armor, offhand, crafting grid, an open
     * container).
     * 找到的一份枪所在位置。HOTBAR 为可见可用的栏位；CURSOR 为正在持有者自身栏位间移动的枪（栏位防护仍允许 PICKUP）；
     * STRAY 为其他任何位置（主背包 9-35，包括 SparkFactionAPI 0.1.5.13+ 显示在快捷栏上方的第二行 27-35、盔甲、副手、
     * 合成格、已打开的容器）。
     */
    enum Location {
        HOTBAR,
        CURSOR,
        STRAY
    }

    /**
     * @param keep  index into the copy list of the one copy that survives, or {@link #NO_COPY}; every other copy is removed
     * @param grant a fresh gun is placed into the hotbar (the item cooldown is per Item, so it is untouched)
     */
    record Decision(int keep, boolean grant) {
    }

    /**
     * An entitled holder keeps exactly one gun: the first hotbar copy, else the cursor copy, else a fresh gun is
     * placed into the hotbar. Duplicates and strays are removed. A non-entitled player keeps nothing.
     * 有资格的持有者恰好保留一把枪：优先第一个快捷栏副本，其次光标副本，否则在快捷栏放入新枪。重复与错放副本被移除。
     * 无资格的玩家什么也不保留。
     *
     * @param copies found copies, hotbar slots in ascending order
     */
    static Decision decide(boolean entitled, List<Location> copies) {
        if (!entitled) {
            return new Decision(NO_COPY, false);
        }
        int keep = copies.indexOf(Location.HOTBAR);
        if (keep == NO_COPY) {
            keep = copies.indexOf(Location.CURSOR);
        }
        return new Decision(keep, keep == NO_COPY);
    }

    /** Every player is swept exactly once per {@link #INTERVAL_TICKS}. / 每名玩家每个间隔恰好清理一次。 */
    static boolean isSweepTick(long worldTime, int entityId) {
        return Math.floorMod(worldTime + entityId, INTERVAL_TICKS) == 0;
    }

    /**
     * Whether this tick reconciles the player: its staggered sweep tick, and only a match participant
     * ({@link OffMatchUse#isMatchParticipant}, read only on that tick). A free holder is skipped entirely: no strip, no
     * grant, no dedupe (owner rule 2026-10-04).
     * 本刻是否校正该玩家：仅在其错峰清理刻，且只校正对局参与者（{@link OffMatchUse#isMatchParticipant}，只在该刻读取）。
     * 自由持有者完全跳过：不收走、不补发、不去重（所有者 2026-10-04 规则）。
     */
    static boolean sweeps(long worldTime, int entityId, BooleanSupplier matchParticipant) {
        return isSweepTick(worldTime, entityId) && matchParticipant.getAsBoolean();
    }

    /**
     * With a full hotbar, the slot whose item is moved out for the gun: the rightmost hotbar slot that is not
     * selected, so the held item (for example Wathe's psycho bat, which must stay selected) is never displaced.
     * 快捷栏已满时为枪腾出的栏位：最右侧且非选中的快捷栏位，因此从不移走手持物品（例如必须保持选中的 Wathe 疯魔球棒）。
     */
    static int displacedHotbarSlot(int selectedSlot) {
        int last = PlayerInventory.getHotbarSize() - 1;
        return selectedSlot == last ? last - 1 : last;
    }

    /**
     * Whether a removed stray's slot may take the displaced hotbar item: main slots 9-35 or the offhand, never armor.
     * 被移除的错放副本所在栏位能否接收被移出的快捷栏物品：主背包 9-35 或副手，从不使用盔甲栏。
     */
    static boolean isStorageSlot(int slot) {
        return (slot >= PlayerInventory.getHotbarSize() && slot < PlayerInventory.MAIN_SIZE)
                || slot == PlayerInventory.OFF_HAND_SLOT;
    }

    /**
     * Where the displaced hotbar item goes, given the slot a removed misplaced gun {@code vacated} (or
     * {@link #NO_SLOT}): with the second row shown, the vacated slot when it is in that row, else its first empty slot,
     * so the item stays visible; then the vacated slot; then main slots 9-35; then an empty offhand; {@link #NO_SLOT}
     * with no room. Without the second row this is the old order (vacated slot, 9-35, offhand).
     * 被移出的快捷栏物品的去处（{@code vacated} 为被移除的错放枪腾出的栏位，或 {@link #NO_SLOT}）：
     * 显示第二行时，若腾出的栏位在该行则用它，否则用该行第一个空栏位，使物品仍可见；然后是腾出的栏位；然后是主背包 9-35；
     * 然后是空副手；没有空间时为 {@link #NO_SLOT}。没有第二行时即旧顺序（腾出的栏位、9-35、副手）。
     */
    static int displacementSlot(boolean secondRowShown, int vacated, IntPredicate emptySlot) {
        if (secondRowShown) {
            if (SparkFactionSecondRowCompat.isSecondRowSlot(vacated)) {
                return vacated;
            }
            for (int slot = SparkFactionSecondRowCompat.SECOND_ROW_START;
                 slot < SparkFactionSecondRowCompat.SECOND_ROW_END; slot++) {
                if (emptySlot.test(slot)) {
                    return slot;
                }
            }
        }
        if (vacated != NO_SLOT) {
            return vacated;
        }
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (emptySlot.test(slot)) {
                return slot;
            }
        }
        return emptySlot.test(PlayerInventory.OFF_HAND_SLOT) ? PlayerInventory.OFF_HAND_SLOT : NO_SLOT;
    }
}
