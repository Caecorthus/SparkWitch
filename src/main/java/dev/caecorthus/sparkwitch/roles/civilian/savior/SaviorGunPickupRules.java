package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Savior already carries their pistol and may not pick up or otherwise obtain other guns: Wathe's gun tag plus
 * SparkWitch's own guns. Server-authoritative and creative-exempt, following the USEC gun rules.
 * 救世主已有手枪，不可再捡或获得其他枪：Wathe 枪械标签加上 SparkWitch 自己的枪械。服务端裁定，创造模式豁免，沿用 USEC 枪械规则。
 */
public final class SaviorGunPickupRules {
    private SaviorGunPickupRules() {
    }

    public static boolean blocks(ServerPlayerEntity player, ItemStack stack) {
        if (player.isCreative()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game.getRole(player) != SparkWitchRoles.savior()) {
            return false;
        }
        // After an innocent-kill punishment the Savior also must not pick the dropped sword back up.
        // 误杀惩罚后，救世主同样不能把掉在地上的光剑捡回来。
        if (stack.isOf(SparkWitchItems.saviorLightsaber())) {
            return game.isPreventedFromGunPickup(player);
        }
        return isGun(stack);
    }

    /** Refuses cursor/quick-move/swap/all clicks that would pull a foreign slot's gun into the Savior. / 拒绝把外部枪械拿进救世主的点击。 */
    public static boolean blocksSlotClick(ScreenHandler handler, PlayerEntity player, int slotIndex,
                                          SlotActionType action) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || player.isCreative()
                || slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return false;
        }
        Slot slot = handler.slots.get(slotIndex);
        if (slot.inventory == player.getInventory() || !blocks(serverPlayer, slot.getStack())) {
            return false;
        }
        if (action != SlotActionType.PICKUP && action != SlotActionType.QUICK_MOVE
                && action != SlotActionType.SWAP && action != SlotActionType.PICKUP_ALL) {
            return false;
        }
        return GameWorldComponent.KEY.get(serverPlayer.getWorld()).getRole(serverPlayer) == SparkWitchRoles.savior();
    }

    public static boolean isGun(ItemStack stack) {
        return stack.isIn(WatheItemTags.GUNS)
                || stack.isOf(SparkWitchItems.saviorPistol())
                || stack.isOf(SparkWitchItems.doubleBarrelShotgun())
                || stack.isOf(SparkWitchItems.usecRifle())
                || stack.isOf(SparkWitchItems.shriekGun());
    }
}
