package dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout;

import java.util.List;
import net.minecraft.entity.player.PlayerInventory;

/**
 * Pure decisions of the Shriek Gun reconcile: which copy (if any) survives, whether a fresh gun is placed, the sweep
 * cadence, and where a gun goes when the hotbar is full. The server executor lives in {@link AbyssListenerLoadout}.
 * 啸音铳校正的纯判定：保留哪一份（如有）、是否放入新枪、清理节奏，以及快捷栏已满时枪放在哪里。
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
     * own slots (the slot guard still allows PICKUP there); STRAY is anywhere else (hidden main slots 9-35, armor,
     * offhand, crafting grid, an open container).
     * 找到的一份枪所在位置。HOTBAR 为可见可用的栏位；CURSOR 为正在持有者自身栏位间移动的枪（栏位防护仍允许 PICKUP）；
     * STRAY 为其他任何位置（隐藏主背包 9-35、盔甲、副手、合成格、已打开的容器）。
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
     * With a full hotbar, the slot whose item is moved out for the gun: the rightmost hotbar slot that is not
     * selected, so the held item (for example Wathe's psycho bat, which must stay selected) is never displaced.
     * 快捷栏已满时为枪腾出的栏位：最右侧且非选中的快捷栏位，因此从不移走手持物品（例如必须保持选中的 Wathe 疯魔球棒）。
     */
    static int displacedHotbarSlot(int selectedSlot) {
        int last = PlayerInventory.getHotbarSize() - 1;
        return selectedSlot == last ? last - 1 : last;
    }
}
