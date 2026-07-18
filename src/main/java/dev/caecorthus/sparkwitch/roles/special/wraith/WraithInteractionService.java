package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.block.DrinkTrayBlock;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.item.CocktailItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

/** Owns the restricted Wraith right-click allowlist. */
final class WraithInteractionService {
    private static boolean registered;

    private WraithInteractionService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!WraithStateService.isRestricted(player)) {
                return ActionResult.PASS;
            }
            Object block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            return block instanceof FoodPlatterBlock
                    || block instanceof DrinkTrayBlock
                    || block instanceof BedBlock
                    ? ActionResult.PASS
                    : ActionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                WraithStateService.isRestricted(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            boolean allowed = stack.contains(DataComponentTypes.FOOD)
                    || stack.getItem() instanceof CocktailItem;
            return WraithStateService.isRestricted(player) && !allowed
                    ? TypedActionResult.fail(stack)
                    : TypedActionResult.pass(stack);
        });
    }
}
