package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateAccess;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithConsumableInventoryRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Low-priority whole-method adapter so NoellesRoles' priority-1100 platter injections remain inside original.call.
 * 低优先级包裹整段方法，让 NoellesRoles priority 1100 的餐盘逻辑先在 original.call 内完整执行。
 */
@Mixin(value = FoodPlatterBlock.class, priority = 1_000)
public abstract class FoodPlatterBlockPoisonAppleMixin {
    @WrapMethod(method = "onUse")
    private ActionResult sparkwitch$handlePoisonApple(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            Operation<ActionResult> original
    ) {
        if (world.isClient) {
            return original.call(state, world, pos, player, hit);
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof BeveragePlateBlockEntity plate)
                || !(blockEntity instanceof PoisonApplePlateAccess poisonApple)) {
            return original.call(state, world, pos, player, hit);
        }

        PoisonApplePlateService.refreshMatch(poisonApple);
        if (PoisonApplePlateService.isHoldingPoisonApple(player)) {
            PoisonApplePlateService.tryArm(player, poisonApple);
            // A duplicate or out-of-match placement is fully rejected instead of falling into native platter logic.
            // 重复或非对局内布置会被完整拒绝，不会继续进入原生餐盘逻辑。
            return ActionResult.SUCCESS;
        }

        boolean antidoteWasReady = PoisonApplePlateService.isReadyAntidote(player);
        boolean hadNativePoison = plate.getPoisoner() != null;
        boolean hadPoisonApple = poisonApple.sparkwitch$isPoisonAppleArmed();
        PlatterCall call = sparkwitch$callPlatter(
                state,
                world,
                pos,
                player,
                hit,
                plate,
                antidoteWasReady,
                original
        );

        if (hadPoisonApple && PoisonApplePlateService.cureWithAntidote(
                world,
                pos,
                player,
                plate,
                poisonApple,
                antidoteWasReady,
                hadNativePoison
        )) {
            return ActionResult.SUCCESS;
        }
        if (!call.acquired().isEmpty()) {
            PoisonApplePlateService.recordSuccessfulTake(call.acquired(), poisonApple);
        }
        return call.result();
    }

    private PlatterCall sparkwitch$callPlatter(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            BeveragePlateBlockEntity plate,
            boolean antidoteWasReady,
            Operation<ActionResult> original
    ) {
        ItemStack heldStack = player.getMainHandStack();
        if (!sparkwitch$mayRelocateHeldConsumable(player, plate, heldStack, antidoteWasReady)) {
            boolean handWasEmpty = heldStack.isEmpty();
            ActionResult result = original.call(state, world, pos, player, hit);
            ItemStack acquired = handWasEmpty ? player.getMainHandStack() : ItemStack.EMPTY;
            return new PlatterCall(result, acquired);
        }

        PlayerInventory inventory = player.getInventory();
        int selectedSlot = inventory.selectedSlot;
        int temporarySlot = inventory.getEmptySlot();
        if (!PlayerInventory.isValidHotbarIndex(selectedSlot) || temporarySlot == PlayerInventory.NOT_FOUND) {
            return new PlatterCall(original.call(state, world, pos, player, hit), ItemStack.EMPTY);
        }

        inventory.setStack(temporarySlot, heldStack);
        inventory.setStack(selectedSlot, ItemStack.EMPTY);
        try {
            ActionResult result = original.call(state, world, pos, player, hit);
            ItemStack acquired = inventory.getStack(selectedSlot);
            sparkwitch$finishRelocation(inventory, selectedSlot, temporarySlot, heldStack, acquired);
            return new PlatterCall(result, acquired);
        } catch (RuntimeException | Error failure) {
            sparkwitch$rollbackRelocation(inventory, selectedSlot, temporarySlot, heldStack, failure);
            throw failure;
        }
    }

    private boolean sparkwitch$mayRelocateHeldConsumable(
            PlayerEntity player,
            BeveragePlateBlockEntity plate,
            ItemStack heldStack,
            boolean antidoteWasReady
    ) {
        return WraithStateService.isRestricted(player)
                && !player.isCreative()
                && !antidoteWasReady
                && !heldStack.isOf(WatheItems.POISON_VIAL)
                && WraithConsumableInventoryRules.isConsumable(heldStack)
                && !plate.getStoredItems().isEmpty()
                && plate.getStoredItems().stream().allMatch(WraithConsumableInventoryRules::isConsumable);
    }

    private void sparkwitch$finishRelocation(
            PlayerInventory inventory,
            int selectedSlot,
            int temporarySlot,
            ItemStack displaced,
            ItemStack acquired
    ) {
        if (inventory.getStack(temporarySlot) != displaced) {
            throw new IllegalStateException("Wraith platter relocation slot changed during interaction");
        }
        inventory.setStack(selectedSlot, displaced);
        inventory.setStack(temporarySlot, acquired);
        inventory.markDirty();
    }

    private void sparkwitch$rollbackRelocation(
            PlayerInventory inventory,
            int selectedSlot,
            int temporarySlot,
            ItemStack displaced,
            Throwable failure
    ) {
        if (inventory.getStack(temporarySlot) != displaced || !inventory.getStack(selectedSlot).isEmpty()) {
            failure.addSuppressed(new IllegalStateException("Unable to restore Wraith platter inventory safely"));
            inventory.markDirty();
            return;
        }
        inventory.setStack(selectedSlot, displaced);
        inventory.setStack(temporarySlot, ItemStack.EMPTY);
        inventory.markDirty();
    }

    private record PlatterCall(ActionResult result, ItemStack acquired) {
    }
}
