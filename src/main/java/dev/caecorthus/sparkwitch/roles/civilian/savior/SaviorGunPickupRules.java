package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Savior already carries their pistol and may not pick up or otherwise obtain other guns: Wathe's gun tag plus
 * SparkWitch's own guns. The Savior's own pistol and lightsaber are never vetoed (taking them back from a chest or a
 * Knight duel hotbar move must work). The hidden revolver in the locked main rows stays put against a modified client;
 * only the innocent-kill punishment moves it into the hand ({@code SaviorRules.swapInRevolver}, owner 2026-11-16).
 * From then on it is an ordinary hotbar item: clicks on it are not refused, and it may come back into the inventory
 * while the Savior carries no other revolver ({@link #blocksInsert}); picking a gun off the ground stays refused.
 * Server-authoritative and creative-exempt, following the USEC gun rules.
 * 救世主已有手枪，不可再捡或获得其他枪：Wathe 枪械标签加上 SparkWitch 自己的枪械。救世主自己的手枪与光剑永不拦截
 * （从箱子取回、骑士决斗移到快捷栏都必须可行）。锁定主背包行中的隐藏左轮防止改包客户端移出；只有误杀惩罚会把它换到手上
 * （{@code SaviorRules.swapInRevolver}，所有者 2026-11-16）。此后它就是普通的快捷栏物品：对它的点击不再被拒绝，并且只要
 * 救世主身上没有别的左轮，它就可以回到物品栏（{@link #blocksInsert}）；从地上捡枪仍被拒绝。服务端裁定，创造模式豁免，
 * 沿用 USEC 枪械规则。
 */
public final class SaviorGunPickupRules {
    private SaviorGunPickupRules() {
    }

    public static boolean blocks(ServerPlayerEntity player, ItemStack stack) {
        return isRestrictedSavior(player) && isForeignGun(stack);
    }

    /**
     * Server, {@code PlayerInventory} insert and offer: a foreign gun is refused, but the Savior's own revolver may
     * come back while the inventory holds no other one. After the punishment the revolver is a hotbar item the player
     * can lift onto the cursor; vanilla {@code ScreenHandler.onClosed} (and Wathe's disconnect path) hand the cursor
     * stack back through offer/insert, and refusing it there would drop the gun at the feet of a Savior who may never
     * pick guns up, or delete it. One revolver per Savior still holds: a second one is refused.
     * 服务端，{@code PlayerInventory} 的插入与 offer：外来枪械被拒绝，但只要物品栏里没有别的左轮，救世主自己的左轮可以放回。
     * 惩罚之后左轮是玩家可以拿到光标上的快捷栏物品；原版 {@code ScreenHandler.onClosed}（以及 Wathe 的断线路径）通过
     * offer/insert 归还光标物品，在那里拒绝会让枪掉在一名永远不能捡枪的救世主脚下，甚至被删除。每名救世主仍只有一把左轮：
     * 第二把会被拒绝。
     */
    public static boolean blocksInsert(ServerPlayerEntity player, ItemStack stack) {
        return blocks(player, stack) && !(stack.isOf(WatheItems.REVOLVER)
                && !player.getInventory().contains(held -> held.isOf(WatheItems.REVOLVER)));
    }

    /**
     * Server, HEAD of {@code ScreenHandler#internalOnSlotClick}. Refuses (1) cursor/quick-move/swap/all clicks that
     * would pull a foreign slot's gun into the Savior, and (2) any click on the Savior's own locked-row slot that holds
     * a Wathe revolver, plus a SWAP whose button names such a slot: Wathe's LimitedInventoryScreen hides rows 9–26 only
     * on the client.
     * 服务端，{@code ScreenHandler#internalOnSlotClick} HEAD。拒绝：(1) 会把外部槽位枪械拿进救世主的光标/快速移动/交换/
     * 全部拾取点击；(2) 对救世主自己锁定行中装有 Wathe 左轮的槽位的任何点击，以及按钮指向此类槽位的 SWAP：Wathe 的
     * LimitedInventoryScreen 只在客户端隐藏 9–26 行。
     */
    public static boolean blocksSlotClick(ScreenHandler handler, PlayerEntity player, int slotIndex, int button,
                                          SlotActionType action) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !isRestrictedSavior(serverPlayer)) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        // Vanilla bounds a SWAP button to 0–8/40; the guard does not rely on that bound. / 原版把 SWAP 按钮限制在 0–8/40；守卫不依赖该限制。
        if (action == SlotActionType.SWAP && holdsLockedRevolver(inventory, button)) {
            return true;
        }
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return false;
        }
        Slot slot = handler.slots.get(slotIndex);
        if (slot.inventory == inventory) {
            return holdsLockedRevolver(inventory, slot.getIndex());
        }
        if (!isForeignGun(slot.getStack())) {
            return false;
        }
        return action == SlotActionType.PICKUP || action == SlotActionType.QUICK_MOVE
                || action == SlotActionType.SWAP || action == SlotActionType.PICKUP_ALL;
    }

    /**
     * Server, HEAD of {@code PlayerInventory#swapSlotWithHotbar} (the pick-from-inventory packet): the locked-row
     * revolver is never pulled into the hotbar. / 服务端，{@code PlayerInventory#swapSlotWithHotbar} HEAD（从背包选取数据包）：
     * 锁定行中的左轮永远不会被拉到快捷栏。
     */
    public static boolean blocksHotbarSwap(ServerPlayerEntity player, int slot) {
        return isRestrictedSavior(player) && holdsLockedRevolver(player.getInventory(), slot);
    }

    /**
     * Guns other than the Savior's own weapons that any holder can fire: the Sniper's M1903A4 joins like the AXMC (both
     * outside {@code wathe:guns}, neither role-gated). 狮身豪's "手"枪 is left out: it fires only for a Sphinx Hao.
     * 救世主自有武器以外、任何持有者都能开火的枪械：狙击手的 M1903A4 与 AXMC 一样列入（二者都不在 {@code wathe:guns} 中，
     * 也都不限职业）。狮身豪的“手”枪不列入：它只对狮身豪开火。
     */
    public static boolean isForeignGun(ItemStack stack) {
        return stack.isIn(WatheItemTags.GUNS)
                || stack.isOf(SparkWitchItems.doubleBarrelShotgun())
                || stack.isOf(SparkWitchItems.usecRifle())
                || stack.isOf(SparkWitchItems.m1903a4())
                || stack.isOf(SparkWitchItems.shriekGun());
    }

    static boolean holdsLockedRevolver(PlayerInventory inventory, int index) {
        return index >= SaviorRules.LOCKED_MAIN_FIRST_SLOT && index <= SaviorRules.LOCKED_MAIN_LAST_SLOT
                && index < inventory.size() && inventory.getStack(index).isOf(WatheItems.REVOLVER);
    }

    private static boolean isRestrictedSavior(ServerPlayerEntity player) {
        return !player.isCreative()
                && SaviorRules.isSavior(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
