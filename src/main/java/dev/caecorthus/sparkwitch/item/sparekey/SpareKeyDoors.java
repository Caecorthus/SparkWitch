package dev.caecorthus.sparkwitch.item.sparekey;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.DENY;
import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.HANDLED;
import static dev.doctor4t.wathe.api.event.DoorInteraction.DoorInteractionResult.PASS;

/**
 * Spare Key doors on Wathe's {@code DoorInteraction.EVENT}, shaped like {@code FisherKeyFishDoors}. Runs on both logical
 * sides: the client predicts HANDLED or DENY with the same checks, and only the server toggles the door, charges the key
 * and writes the cooldown. Wathe's {@code DoorBlockEntity#toggle} ignores a second toggle in the same tick, so the
 * server toggles the door itself and charges only after the whole doorway really opened. Creative and spectator players,
 * a cooling key, a stopped round, and open, blasted or jammed doors PASS to Wathe's own handling.
 * Wathe {@code DoorInteraction.EVENT} 上的备用钥匙开门逻辑，结构与 {@code FisherKeyFishDoors} 相同。两个逻辑端都运行：客户端用
 * 同样的判定预测 HANDLED 或 DENY，只有服务端切换门、扣耐久并写冷却。Wathe 的 {@code DoorBlockEntity#toggle} 会忽略同一 tick
 * 的第二次切换，所以服务端自己切换门，并只在整道门确实打开后才扣点。创造与旁观玩家、冷却中的钥匙、未进行的对局，以及已开、
 * 被炸开或被卡住的门都交给 Wathe 自己处理（PASS）。
 */
public final class SpareKeyDoors {
    /** Vanilla's server-side reach slack for block use. / 原版服务端方块交互的触及余量。 */
    private static final double REACH_SLACK = 1.0D;
    private static boolean registered;

    private SpareKeyDoors() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorInteraction.EVENT.register(SpareKeyDoors::interact);
        SpareKeyReplayFormatter.register();
    }

    private static DoorInteraction.DoorInteractionResult interact(DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        // Creative opens free through Wathe's own creative path. / 创造模式经 Wathe 自身的创造路径免费开门。
        if (player.isSpectator() || player.isCreative()
                || !(context.getEntity() instanceof SmallDoorBlockEntity door)) {
            return PASS;
        }
        Hand hand = keyHand(context, player);
        // While cooling down the key does nothing: Wathe handles the click as if no key were held.
        // 冷却期间钥匙不起作用：Wathe 按未持钥匙处理这次点击。
        if (hand == null || player.getItemCooldownManager().isCoolingDown(SparkWitchItems.spareKey())) {
            return PASS;
        }
        var world = context.getWorld();
        var lowerState = world.getBlockState(context.getLowerPos());
        SmallDoorBlockEntity neighbor = SmallDoorBlock.getNeighborDoorEntity(lowerState, world, context.getLowerPos());
        boolean train = context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR;
        if (!SpareKeyRules.canUnlock(door.isOpen(), door.isBlasted(),
                door.isJammed() || neighbor != null && neighbor.isJammed(), context.requiresKey(), train,
                GameWorldComponent.KEY.get(world).isRunning())) {
            // PASS keeps Wathe's own jammed/locked feedback. / PASS 保留 Wathe 自有的卡门/上锁提示。
            return PASS;
        }

        ItemStack key = player.getStackInHand(hand);
        int cost = SpareKeyRules.doorCost(train);
        int remaining = SpareKeyItem.remaining(key);
        if (!SpareKeyRules.canPay(remaining, cost)) {
            // Both sides deny, so the client never predicts an opening; only the server says why. Nothing is spent.
            // 两端都拒绝，客户端不会预测开门；只有服务端提示原因。不扣任何点数。
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.translatable("item.sparkwitch.spare_key.warning.low_durability",
                        remaining, cost).formatted(Formatting.RED), true);
            }
            return DENY;
        }
        if (world.isClient()) {
            return HANDLED;
        }

        // toggleOpen already toggles the paired leaf; a same-tick repeat is ignored, so check the result.
        // toggleOpen 已联动另一扇门；同 tick 的重复切换会被忽略，所以要检查结果。
        door.toggle(false);
        if (!door.isOpen() || neighbor != null && !neighbor.isOpen()) {
            return HANDLED;
        }
        key.setDamage(key.getDamage() + cost);
        if (key.getDamage() >= key.getMaxDamage()) {
            // Zero points remove the key instead of leaving a broken one. / 点数归零直接移除，不留损坏的钥匙。
            player.setStackInHand(hand, ItemStack.EMPTY);
        }
        player.getItemCooldownManager().set(SparkWitchItems.spareKey(), SpareKeyRules.COOLDOWN_TICKS);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.getInventory().markDirty();
            serverPlayer.currentScreenHandler.sendContentUpdates();
            GameRecordManager.recordItemUse(serverPlayer, SpareKeyRules.ITEM_ID, null, null);
        }
        return HANDLED;
    }

    /**
     * The hand whose key this use spends, or null.
     * <p>External seam: the Seeker car ({@code SeekerCarUseService}) and the Black Raven crow
     * ({@code BlackRavenCrowUseService}) raise this event with {@code handItem(ItemStack.EMPTY)} and the body as the
     * player, and Wathe copies the hand item, so an empty event hand cannot tell a remote use from a real empty-handed
     * click. The off-hand key therefore also requires that the body is not remote-locked (the exact predicates that fail
     * the body's own block use during those sessions, so a real click is never refused) and that the body could reach
     * the door itself (vanilla's own reach rule, a fail-closed backstop for any other remote dispatcher).
     * 本次使用消耗哪只手上的钥匙；没有则返回 null。
     * <p>外部接缝：搜寻者小车（{@code SeekerCarUseService}）与黑羽鸦乌鸦（{@code BlackRavenCrowUseService}）以
     * {@code handItem(ItemStack.EMPTY)}、本体为玩家触发此事件，而 Wathe 会复制手持物品，所以单凭事件手持为空无法区分远程使用与
     * 真实的空手点击。因此副手钥匙还要求本体未被远程锁定（正是这两种会话期间让本体自身方块交互失败的判定，真实点击不会被误拒），
     * 且本体自己够得着这扇门（原版自身的触及规则，作为对其他远程分发的失败即关闭兜底）。
     */
    private static Hand keyHand(DoorInteraction.DoorInteractionContext context, PlayerEntity player) {
        ItemStack eventHand = context.getHandItem();
        ItemStack mainHand = player.getMainHandStack();
        boolean mayUseOffHand = eventHand.isEmpty() && mainHand.isEmpty()
                && player.getOffHandStack().isOf(SparkWitchItems.spareKey());
        SpareKeyRules.KeyHand hand = SpareKeyRules.keyHand(
                eventHand.isOf(SparkWitchItems.spareKey()),
                mainHand.isOf(SparkWitchItems.spareKey()),
                eventHand.isEmpty(),
                mainHand.isEmpty(),
                player.getOffHandStack().isOf(SparkWitchItems.spareKey()),
                // Only evaluated for an off-hand candidate. / 仅在副手候选时才判定。
                mayUseOffHand && (SeekerRemoteSessionService.isLocked(player)
                        || BlackRavenCrowSessions.isActive(player)),
                !mayUseOffHand || player.canInteractWithBlockAt(context.getPos(), REACH_SLACK));
        return switch (hand) {
            case MAIN -> Hand.MAIN_HAND;
            case OFF -> Hand.OFF_HAND;
            case NONE -> null;
        };
    }
}
