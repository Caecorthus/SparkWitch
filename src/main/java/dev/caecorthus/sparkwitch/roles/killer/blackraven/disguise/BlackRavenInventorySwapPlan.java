package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure slot planner for the no-drop swap: which live slots are stashed, where incoming stacks land
 * (original slot, else the first free main-inventory slot), and which stay in overflow. Never plans a drop.
 * 无丢弃交换的纯槽位规划：哪些当前槽位被存档、传入物品放在哪里（原槽位，否则第一个空的主背包槽位），
 * 哪些留在溢出区。从不规划丢弃。
 */
public final class BlackRavenInventorySwapPlan {
    /** Fallback placement only uses the 36 main slots, never armor or offhand. / 回退放置只用 36 个主槽位。 */
    static final int MAIN_SLOT_COUNT = 36;

    private BlackRavenInventorySwapPlan() {
    }

    /** An incoming stack (by index) placed into a player slot. / 传入物品（按索引）放入的玩家槽位。 */
    public record Placement(int incomingIndex, int targetSlot) {
    }

    public record Plan(List<Integer> stashedSlots, List<Placement> placements, List<Integer> overflowIncomingIndices) {
        public Plan {
            stashedSlots = List.copyOf(stashedSlots);
            placements = List.copyOf(placements);
            overflowIncomingIndices = List.copyOf(overflowIncomingIndices);
        }
    }

    /**
     * Occupied, non-pinned slots are stashed first. Incoming stacks then keep their preferred slot when it is
     * free after stashing (first claimant wins), otherwise take the first free main slot in index order; the
     * rest stay in overflow.
     * 先存档已占用且未固定的槽位；传入物品在存档后原槽位空闲时放回原槽位（先到先得），否则放入按序第一个
     * 空闲主槽位；其余留在溢出区。
     *
     * @param occupied live slot occupancy (41 entries)
     * @param pinned   live slots that stay in place (41 entries)
     * @param incomingPreferredSlots preferred slot of each incoming stack, or -1 for "any free slot"
     */
    public static Plan plan(boolean[] occupied, boolean[] pinned, int[] incomingPreferredSlots) {
        int slotCount = BlackRavenDisguiseRules.PLAYER_SLOT_COUNT;
        if (occupied.length != slotCount || pinned.length != slotCount) {
            throw new IllegalArgumentException("Expected " + slotCount + " player slots");
        }
        List<Integer> stashed = new ArrayList<>();
        boolean[] free = new boolean[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            boolean stays = occupied[slot] && pinned[slot];
            free[slot] = !stays;
            if (occupied[slot] && !pinned[slot]) {
                stashed.add(slot);
            }
        }

        int[] targets = new int[incomingPreferredSlots.length];
        for (int index = 0; index < incomingPreferredSlots.length; index++) {
            int preferred = incomingPreferredSlots[index];
            targets[index] = -1;
            if (preferred >= 0 && preferred < slotCount && free[preferred]) {
                free[preferred] = false;
                targets[index] = preferred;
            }
        }
        List<Placement> placements = new ArrayList<>();
        List<Integer> overflow = new ArrayList<>();
        int cursor = 0;
        for (int index = 0; index < incomingPreferredSlots.length; index++) {
            if (targets[index] < 0) {
                while (cursor < MAIN_SLOT_COUNT && !free[cursor]) {
                    cursor++;
                }
                if (cursor < MAIN_SLOT_COUNT) {
                    free[cursor] = false;
                    targets[index] = cursor;
                }
            }
            if (targets[index] >= 0) {
                placements.add(new Placement(index, targets[index]));
            } else {
                overflow.add(index);
            }
        }
        return new Plan(stashed, placements, overflow);
    }
}
