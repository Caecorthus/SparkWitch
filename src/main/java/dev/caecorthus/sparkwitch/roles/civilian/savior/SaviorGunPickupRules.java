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
 * Knight duel hotbar move must work). The hidden revolver in the locked main rows stays put against a modified client.
 * Server-authoritative and creative-exempt, following the USEC gun rules.
 * 救世主已有手枪，不可再捡或获得其他枪：Wathe 枪械标签加上 SparkWitch 自己的枪械。救世主自己的手枪与光剑永不拦截
 * （从箱子取回、骑士决斗移到快捷栏都必须可行）。锁定主背包行中的隐藏左轮防止改包客户端移出。服务端裁定，创造模式豁免，
 * 沿用 USEC 枪械规则。
 */
public final class SaviorGunPickupRules {
    private SaviorGunPickupRules() {
    }

    public static boolean blocks(ServerPlayerEntity player, ItemStack stack) {
        return isRestrictedSavior(player) && isForeignGun(stack);
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

    /** Guns other than the Savior's own weapons. / 救世主自有武器以外的枪械。 */
    public static boolean isForeignGun(ItemStack stack) {
        return stack.isIn(WatheItemTags.GUNS)
                || stack.isOf(SparkWitchItems.doubleBarrelShotgun())
                || stack.isOf(SparkWitchItems.usecRifle())
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
