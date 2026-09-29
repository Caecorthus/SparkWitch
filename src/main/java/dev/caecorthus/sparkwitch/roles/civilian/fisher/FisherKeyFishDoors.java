package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.HANDLED;
import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.PASS;

/** Key Fish door opening on Wathe's DoorInteraction event. / 钥匙鱼开门（Wathe DoorInteraction 事件）。 */
public final class FisherKeyFishDoors {
    private static boolean registered;

    private FisherKeyFishDoors() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorInteraction.EVENT.register(FisherKeyFishDoors::interact);
    }

    private static DoorInteraction.DoorInteractionResult interact(DoorInteraction.DoorInteractionContext context) {
        var player = context.getPlayer();
        ItemStack held = player.getMainHandStack();
        if (!held.isOf(SparkWitchItems.keyFish()) || player.isSpectator() || player.isCreative()
                || !(context.getEntity() instanceof SmallDoorBlockEntity door)) {
            return PASS;
        }
        var world = context.getWorld();
        var lowerState = world.getBlockState(context.getLowerPos());
        SmallDoorBlockEntity neighbor = SmallDoorBlock.getNeighborDoorEntity(lowerState, world, context.getLowerPos());
        boolean train = context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR;
        if (!canUnlock(door.isOpen(), door.isBlasted(), door.isJammed() || neighbor != null && neighbor.isJammed(),
                context.requiresKey(), train, GameWorldComponent.KEY.get(world).isRunning())) {
            // PASS preserves Wathe's own jammed/locked feedback. Vanilla metal-sheet/cockpit doors are hand-openable.
            // PASS 保留 Wathe 自有的卡门/锁门提示；原版实现的金属板门及驾驶舱门本来就能徒手打开。
            return PASS;
        }
        if (world.isClient()) {
            return HANDLED;
        }

        // Wathe's toggleOpen already toggles the paired leaf; a second same-tick toggle is ignored.
        // Wathe 的 toggleOpen 已联动另一扇门，同 tick 的再次切换会被忽略。
        door.toggle(false);
        if (!door.isOpen()) {
            return HANDLED;
        }
        if (neighbor == null || neighbor.isOpen()) {
            // Context handItem is a copy; consume the actual held stack only after the whole doorway opens.
            // context.handItem 是副本；整道门确实打开后才扣除真实手持栈。toggle 保留锁名及五秒自动关门。
            held.decrement(1);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                FisherInventory.sync(serverPlayer);
            }
        }
        return HANDLED;
    }

    static boolean canUnlock(boolean open, boolean blasted, boolean jammed, boolean requiresKey,
                             boolean train, boolean running) {
        return !open && !blasted && !jammed && (train ? running : requiresKey);
    }
}
