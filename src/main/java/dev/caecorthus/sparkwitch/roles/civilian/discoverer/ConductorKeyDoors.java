package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.ALLOW;
import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.DENY;
import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.PASS;

/**
 * Conductor-key door handling on Wathe's {@code DoorInteraction.EVENT}.
 * <p>Opens exactly what a master key opens: a train door costs
 * {@link DiscovererRules#CONDUCTOR_KEY_TRAIN_DOOR_COST} points, a small door locked by a room key costs
 * {@link DiscovererRules#CONDUCTOR_KEY_ROOM_DOOR_COST}. An attempt that cannot pay that door's cost is denied
 * outright: the door stays shut and nothing is deducted.
 * 乘务员钥匙的开门逻辑（Wathe {@code DoorInteraction.EVENT}）。
 * <p>能开万能钥匙能开的东西，按门类型计价：列车门消耗
 * {@link DiscovererRules#CONDUCTOR_KEY_TRAIN_DOOR_COST} 点，被房间钥匙锁住的小木门消耗
 * {@link DiscovererRules#CONDUCTOR_KEY_ROOM_DOOR_COST} 点。耐久不够这扇门的消耗时整次交互被拒绝：
 * 门不开，耐久也不扣。
 */
public final class ConductorKeyDoors {

    private static boolean registered;

    private ConductorKeyDoors() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorInteraction.EVENT.register(ConductorKeyDoors::interact);
    }

    private static DoorInteraction.DoorInteractionResult interact(DoorInteraction.DoorInteractionContext context) {
        if (context.isOpen() || context.isBlasted() || context.isJammed()) {
            return PASS;
        }
        // Same reach as a master key: train doors, plus any door whose key name is set.
        // 与万能钥匙同范围：列车门，以及任何设了钥匙名的门。
        if (context.getDoorType() != DoorInteraction.DoorType.TRAIN_DOOR && !context.requiresKey()) {
            return PASS;
        }

        PlayerEntity player = context.getPlayer();
        Hand hand = heldKeyHand(player);
        if (hand == null) {
            return PASS;
        }
        // Creative and spectator keep the master-key convenience of never consuming the key.
        // 创造与旁观保持万能钥匙的便利：不消耗钥匙。
        if (context.isCreative() || player.isSpectator()) {
            return ALLOW;
        }

        ItemStack key = player.getStackInHand(hand);
        // Train doors cost more than room doors; the checked-and-charged amount is always this door's own price.
        // 列车门比房间门贵；校验和实际扣除的始终是这扇门自己的价格。
        int cost = doorCost(context);
        int remaining = ConductorKeyItem.remaining(key);
        if (remaining < cost) {
            // Not enough left: the whole attempt is refused, and the remaining points stay untouched. Both sides deny
            // so the client never predicts an opening, and only the server tells the player why.
            // 剩余不够一次消耗：整次尝试作废，剩余耐久原封不动。两端都拒绝，客户端就不会先做出开门的预判；
            // 只有服务端负责提示原因。
            if (context.isServerSide() && player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.translatable(
                        "item.sparkwitch.conductor_key.warning.low_durability",
                        remaining,
                        cost
                ).formatted(Formatting.RED), true);
            }
            return DENY;
        }
        if (!context.isServerSide()) {
            return ALLOW;
        }

        key.setDamage(key.getDamage() + cost);
        if (key.getDamage() >= key.getMaxDamage()) {
            // Zero durability removes the key instead of leaving a broken one behind.
            // 耐久归零直接消失，不留下损坏的钥匙。
            player.setStackInHand(hand, ItemStack.EMPTY);
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.getInventory().markDirty();
            serverPlayer.currentScreenHandler.sendContentUpdates();
            // One replay line per use, as the owner's spec lists it. / 按所有者规格每次使用记一条回放。
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    serverPlayer, DiscovererRules.ROLE_ID, null, null);
        }
        return ALLOW;
    }

    /** Durability this door costs: 3 for a train door, 1 for a room-locked small door. 这扇门的耐久价格：列车门 3、被锁小木门 1。 */
    private static int doorCost(DoorInteraction.DoorInteractionContext context) {
        return context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR
                ? DiscovererRules.CONDUCTOR_KEY_TRAIN_DOOR_COST
                : DiscovererRules.CONDUCTOR_KEY_ROOM_DOOR_COST;
    }

    private static Hand heldKeyHand(PlayerEntity player) {
        if (player.getMainHandStack().isOf(SparkWitchItems.conductorKey())) {
            return Hand.MAIN_HAND;
        }
        if (player.getOffHandStack().isOf(SparkWitchItems.conductorKey())) {
            return Hand.OFF_HAND;
        }
        return null;
    }
}
