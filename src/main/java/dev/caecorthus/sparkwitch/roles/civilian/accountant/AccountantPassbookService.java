package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.item.BoundItemRules;
import dev.doctor4t.wathe.api.event.ShouldDropOnDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/**
 * 存折 passbook runtime (server authority). Stored coins live in the stack's custom data
 * ({@link AccountantRules#STORED_COINS_KEY}), so they travel with the item.
 * <ul>
 *   <li>Deposit (right-click): only a living Accountant ({@link AccountantRules#depositVerdict}); the whole balance
 *   moves in.</li>
 *   <li>Withdraw (left-click, {@code AccountantWithdrawC2SPacket}): any living holder with the passbook in the main
 *   hand; everything comes out and the passbook is used up. An empty passbook is kept.</li>
 *   <li>Drop (sneak + right-click): any living holder in a running round, through vanilla {@code dropItem} (40-tick
 *   pickup delay, thrower set).
 *   This deliberately bypasses Wathe's in-round drop block for this one item (owner 2026-11-04).</li>
 *   <li>Death: Wathe's {@code ShouldDropOnDeath} drops it for whoever carries it, so coins never stay in a dead
 *   inventory; a dropped passbook never despawns (Wathe clears dropped items at round end).</li>
 *   <li>World use: item frames, armor stands, allays and decorated pots never take it, since they survive Wathe's
 *   round reset and would carry stored coins into the next round.</li>
 * </ul>
 * Transfers use {@code setBalance}, never {@code addToBalance}: the coins were already earned, so income observers
 * (SparkFactionAPI's income record, SparkTraits Snowball, and SparkStrength's killer team purse — owner 2026-11-04:
 * the shared purse does not take passbook coins) must not count them again.
 * 存折运行逻辑（服务端权威）。存入的金币保存在物品的自定义数据（{@link AccountantRules#STORED_COINS_KEY}）中，随物品移动。
 * 存入（右键）：只有存活的会计（{@link AccountantRules#depositVerdict}），全部余额存入。
 * 取出（左键，{@code AccountantWithdrawC2SPacket}）：任何主手持有存折的存活持有者，全部取出，存折用掉；空存折保留。
 * 丢出（潜行 + 右键）：对局进行中任何存活的持有者，经由原版 {@code dropItem}（40 刻拾取延迟、记录丢出者）。这是有意只对这一物品绕过 Wathe
 * 的局内禁丢（所有者 2026-11-04）。
 * 死亡：Wathe 的 {@code ShouldDropOnDeath} 让任何携带者死亡时掉落存折，金币不会留在死人背包里；掉落的存折永不消失（Wathe
 * 在回合结束时清除掉落物）。
 * 世界交互：物品展示框、盔甲架、悦灵与饰纹陶罐绝不收走存折，因为它们能躲过 Wathe 的回合重置，会把存款带到下一局。
 * 转账使用 {@code setBalance}，从不使用 {@code addToBalance}：这些金币已经赚过一次，收入观察者（SparkFactionAPI 收入记录、
 * SparkTraits 滚雪球、SparkStrength 杀手共享钱包——所有者 2026-11-04：共享钱包不吃存折）不得再次计入。
 */
public final class AccountantPassbookService {
    private static boolean registered;

    private AccountantPassbookService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShouldDropOnDeath.EVENT.register((stack, victim) -> isPassbook(stack));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item && isPassbook(item.getStack())) {
                item.setNeverDespawn();
            }
        });
        // Item frames, armor stands and allays never take a passbook (they survive Wathe's round reset, so parked
        // coins would reach the next round); decorated pots are DecoratedPotBlockAccountantPassbookMixin. Runs on both
        // sides: the client stops before sending, the server refuses a forged packet (the bound-item guard shape).
        // 物品展示框、盔甲架与悦灵绝不收走存折（它们能躲过 Wathe 的回合重置，存进去的钱会带到下一局）；饰纹陶罐由
        // DecoratedPotBlockAccountantPassbookMixin 处理。双端执行：客户端在发包前拦截，服务端拒绝伪造的数据包（绑定物品拦截的形式）。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocksEntityUse(player.getStackInHand(hand), entity) ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean isPassbook(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.accountantPassbook());
    }

    static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isPassbook(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /** Asked by {@code DecoratedPotBlockAccountantPassbookMixin}. / 由该 mixin 调用。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isPassbook(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /** Coins stored in this stack (0 when none); safe on both sides. / 此物品中存入的金币（没有则为 0）；两端均可调用。 */
    public static int storedCoins(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(AccountantRules.STORED_COINS_KEY)) {
            return 0;
        }
        return Math.max(0, data.copyNbt().getInt(AccountantRules.STORED_COINS_KEY));
    }

    /** Merges into existing custom data rather than replacing it. / 合并进已有的自定义数据，而不是整体替换。 */
    static void setStoredCoins(ItemStack stack, int amount) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            if (amount > 0) {
                nbt.putInt(AccountantRules.STORED_COINS_KEY, amount);
            } else {
                nbt.remove(AccountantRules.STORED_COINS_KEY);
            }
        });
    }

    /** Right-click without sneaking. / 不潜行时右键。 */
    static void deposit(ServerPlayerEntity player, ItemStack stack) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        int balance = shop.getBalance();
        switch (AccountantRules.depositVerdict(canAct(player), AccountantRules.isAccountant(game.getRole(player)),
                balance)) {
            case DEPOSIT -> {
                setStoredCoins(stack, AccountantRules.saturatingAdd(storedCoins(stack), balance));
                shop.setBalance(0);
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENDER_CHEST_OPEN,
                        SoundCategory.PLAYERS, 0.6F, 1.4F);
                player.sendMessage(Text.translatable("message.sparkwitch.accountant.deposited", balance)
                        .formatted(Formatting.GOLD), true);
                AccountantReplay.recordDeposit(player, balance);
            }
            case NOT_ACCOUNTANT -> player.sendMessage(Text.translatable(
                    "message.sparkwitch.accountant.not_accountant").formatted(Formatting.GRAY), true);
            case NO_COINS -> player.sendMessage(Text.translatable("message.sparkwitch.accountant.no_coins")
                    .formatted(Formatting.GRAY), true);
            case REFUSED -> {
            }
        }
    }

    /**
     * Sneak + right-click: throws the passbook (coins and all) like a vanilla Q drop and empties the hand.
     * 潜行 + 右键：像原版 Q 键一样丢出存折（金币一并带走）并清空该手。
     */
    static void drop(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        // Same gate as deposit and withdraw: no drop outside a running round or in creative (a dropped passbook never
        // despawns). / 与存取相同的门槛：对局未进行或创造模式下不丢出（丢出的存折永不消失）。
        if (!canAct(player)) {
            return;
        }
        ItemStack dropped = stack.copy();
        player.setStackInHand(hand, ItemStack.EMPTY);
        if (player.dropItem(dropped, false, true) == null) {
            // Nothing could be thrown: give it back rather than lose the coins. / 未能丢出：放回手中，避免金币丢失。
            player.setStackInHand(hand, dropped);
            return;
        }
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.6F, 0.7F);
    }

    /**
     * Server half of the left-click: the client mixin only checks the held item, so every gate is re-checked here.
     * Runs on the server thread (Fabric play payload receivers).
     * 左键的服务端处理：客户端 mixin 只检查手持物品，因此这里重新检查所有门槛。在服务端线程执行（Fabric 游戏阶段载荷接收器）。
     */
    public static void handleWithdrawRequest(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        boolean holding = isPassbook(stack);
        int stored = holding ? storedCoins(stack) : 0;
        switch (AccountantRules.withdrawVerdict(canAct(player), holding, stored)) {
            case WITHDRAW -> {
                PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
                shop.setBalance(AccountantRules.saturatingAdd(shop.getBalance(), stored));
                stack.decrement(1);
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                        SoundCategory.PLAYERS, 0.8F, 1.0F);
                player.sendMessage(Text.translatable("message.sparkwitch.accountant.withdrew", stored)
                        .formatted(Formatting.GOLD), true);
                AccountantReplay.recordWithdraw(player, stored);
            }
            case EMPTY -> player.sendMessage(Text.translatable("message.sparkwitch.accountant.empty")
                    .formatted(Formatting.GRAY), true);
            case REFUSED -> {
            }
        }
    }

    private static boolean canAct(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return AccountantRules.canAct(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), player.isSpectator(), player.isCreative(),
                SparkWitchApi.isWraithRestricted(player));
    }
}
