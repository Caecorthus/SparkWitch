package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Binding of the Sphinx Hao kit (the 「“手”枪」 and its 「拳头子弹」), used by the role-owned guards in
 * {@code mixin/sphinxhao/} and {@link SphinxHaoGunFeatureService}: neither ever becomes an item entity, leaves its
 * holder's own slots (strict {@link BoundItemRules} click policy), goes into an item frame, armor stand, allay or
 * decorated pot, or drops on death; every copy is deleted when its holder dies (BEAR / USEC model). Identified by class,
 * so the checks are safe before registry lookups. There is no creative exemption.
 * 狮身豪装备（「“手”枪」与「拳头子弹」）的绑定规则，供 {@code mixin/sphinxhao/} 中本职业自有的防护与
 * {@link SphinxHaoGunFeatureService} 使用：两者都不会变成掉落物、离开持有者自身栏位（{@link BoundItemRules} 严格点击策略）、
 * 放进物品展示框、盔甲架、悦灵或饰纹陶罐，也不会在死亡时掉落；持有者死亡时所有副本被删除（沿用 BEAR / USEC 模式）。按物品类识别，
 * 因此不依赖注册表获取。没有创造模式豁免。
 */
public final class SphinxHaoGunInventory {
    private SphinxHaoGunInventory() {
    }

    public static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SphinxHaoHandGunItem;
    }

    public static boolean isAmmo(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SphinxHaoFistAmmoItem;
    }

    /** The gun or its ammo. / 手枪或其子弹。 */
    public static boolean isBound(@Nullable ItemStack stack) {
        return isGun(stack) || isAmmo(stack);
    }

    /** Refuses every drop into an item entity (Q, Ctrl+Q, any server path). / 拒绝丢成掉落物（Q、Ctrl+Q 与任何服务端路径）。 */
    public static boolean blocksDrop(@Nullable ItemStack stack) {
        return isBound(stack);
    }

    /** Excludes the kit from Wathe's death-drop loop. / 将装备排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isBound(stack);
    }

    /** Item frames, armor stands and allays would keep the held stack. / 物品展示框、盔甲架与悦灵会收走手持物品。 */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /** Decorated pot counterpart, asked by {@code DecoratedPotBlockSphinxHaoGunMixin}. / 饰纹陶罐版本。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isBound(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /** Server slot-click veto through the strict policy. / 经严格策略的服务端栏位点击否决。 */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        return BoundItemRules.blocksStrictSlotClick(player, slotIndex, button, actionType,
                SphinxHaoGunInventory::isBound);
    }

    /**
     * Pure: a refused drop is handed straight back only to a living holder and only for the cursor stack a closing
     * screen returns (vanilla clears the cursor afterwards, so a refusal would delete it). Any other refused drop either
     * never left the inventory or is a copy (vanilla {@code /give}'s template), so handing it back would duplicate it.
     * 纯函数：被拒绝的丢弃只有在持有者存活、且是关闭界面时退回的光标物品时才放回（原版随后会清空光标，拒绝就会删除它）。其他被拒绝的
     * 丢弃要么从未离开背包，要么是副本（原版 {@code /give} 的模板），放回会复制出一份。
     */
    static boolean keepsRefusedDrop(boolean alive, boolean cursorReturn) {
        return alive && cursorReturn;
    }

    /**
     * Follow-up of a refused drop ({@code PlayerEntitySphinxHaoGunMixin}): a living holder's returning cursor stack goes
     * back into the first free slot; with every slot full the selected hotbar stack (never a kit item) is dropped to make
     * room, so nothing is destroyed. A dead holder's copy just vanishes.
     * 丢弃被拒后的处理（{@code PlayerEntitySphinxHaoGunMixin}）：存活持有者退回的光标物品放回第一个空栏位；所有栏位都满时丢出所选
     * 快捷栏物品（绝不是装备物品）腾出位置，因此不会销毁任何东西。已死亡持有者的副本直接消失。
     */
    public static void keepRefusedDrop(PlayerEntity player, ItemStack refused) {
        if (!(player instanceof ServerPlayerEntity holder)
                || !keepsRefusedDrop(holder.isAlive(), holder.currentScreenHandler.getCursorStack() == refused)) {
            return;
        }
        // Empties the cursor stack itself, so the item exists exactly once whatever the caller does next.
        // 直接清空光标物品堆本身，使调用方之后无论如何处理，该物品都只存在一份。
        ItemStack kept = refused.copyAndEmpty();
        PlayerInventory inventory = holder.getInventory();
        if (!inventory.insertStack(kept)) {
            int slot = inventory.selectedSlot;
            ItemStack displaced = inventory.getStack(slot);
            if (isBound(displaced)) {
                return;
            }
            inventory.setStack(slot, kept);
            holder.dropItem(displaced, false);
        }
        inventory.markDirty();
    }

    /** First fist ammo stack anywhere in the inventory (offhand included), else EMPTY. / 背包中第一组拳头子弹，否则为空。 */
    static ItemStack firstAmmo(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isAmmo(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Deletes every kit item a player holds: inventory, cursor, open screen slots and the 2x2 crafting grid.
     * 删除玩家身上所有装备物品：背包、光标、已打开界面的栏位与 2x2 合成格。
     */
    static void strip(ServerPlayerEntity player) {
        boolean changed = clear(player.getInventory());
        ScreenHandler open = player.currentScreenHandler;
        if (isBound(open.getCursorStack())) {
            open.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : open.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && isBound(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        changed |= clear(player.playerScreenHandler.getCraftingInput());
        if (changed) {
            player.getInventory().markDirty();
            open.syncState();
        }
    }

    private static boolean clear(Inventory inventory) {
        boolean changed = false;
        for (int index = 0; index < inventory.size(); index++) {
            if (isBound(inventory.getStack(index))) {
                inventory.setStack(index, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }
}
