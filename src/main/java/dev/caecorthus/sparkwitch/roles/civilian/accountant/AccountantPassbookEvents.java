package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * 存折 takes its coins back on a left-click, and a left-click with the passbook never lands as an attack.
 * 存折用左键取回金币，且手持存折时的左键不会真的打出去。
 */
public final class AccountantPassbookEvents {

    private AccountantPassbookEvents() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ItemStack stack = serverPlayer.getStackInHand(hand);
            if (!stack.isOf(dev.caecorthus.sparkwitch.SparkWitchItems.accountantPassbook())) {
                return ActionResult.PASS;
            }
            AccountantPassbookItem.withdraw(serverPlayer, stack);
            // Cancel the swing so the accountant never hurts anyone with the passbook in hand.
            // 取消这次挥击，会计手持存折时不会伤到任何人。
            return ActionResult.FAIL;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ItemStack stack = serverPlayer.getStackInHand(hand);
            if (!stack.isOf(dev.caecorthus.sparkwitch.SparkWitchItems.accountantPassbook())) {
                return ActionResult.PASS;
            }
            AccountantPassbookItem.withdraw(serverPlayer, stack);
            return ActionResult.FAIL;
        });
    }
}
