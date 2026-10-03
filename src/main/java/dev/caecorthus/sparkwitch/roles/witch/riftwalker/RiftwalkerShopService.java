package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop.AccompliceShopRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Builds the Riftwalker shop: every plain Accomplice entry, then the Rift Gate (0 gold, unlimited stock, no purchase
 * cooldown, 50 mana charged inside {@code onBuy}). Time Stealer pattern: the list is built from the exact synced role
 * only (never round state, because Wathe caches stock limits during STARTING); {@code ShopPurchase.BEFORE} only
 * explains a refusal (unavailable, hotbar full, mana) and never returns {@code allow}, so later listeners (SparkTraits
 * restrictions) still run. A bought gate stacks onto an existing Rift Gate stack first (D4: unlimited gates).
 * The entry costs 0 gold, so SparkTraits Charisma never wraps it. SparkStrength appends its tablet after ours.
 * 构建隙行者商店：先放入普通共犯的全部条目，再加入裂隙门（0 金币、不限库存、无购买冷却，在 {@code onBuy} 中扣除 50 魔力）。
 * 沿用窃时者模式：只按已同步的精确职业构建列表（从不依赖对局状态，因为 Wathe 在 STARTING 阶段缓存库存上限）；
 * {@code ShopPurchase.BEFORE} 只负责说明拒绝原因（不可购买、快捷栏已满、魔力不足）、从不返回允许，因此后续监听器
 * （SparkTraits 限制）仍会运行。买到的门优先叠到已有的裂隙门堆上（D4：门不限数量）。
 * 商品为 0 金币，SparkTraits 魅力从不包装它。SparkStrength 会在我们之后追加平板。
 *
 * <p>Authority: only the server charges mana, atomically with the hotbar insert (refunded when the insert fails).
 * The client merely labels the price through {@code WitchShopClientTexts}.
 * 权威：只有服务端扣除魔力，并与放入快捷栏原子执行（放入失败则退还）。客户端仅通过 {@code WitchShopClientTexts} 显示价格。
 */
public final class RiftwalkerShopService {
    static final String NOT_ENOUGH_MANA_DENY_KEY = "shop.error.sparkwitch.not_enough_mana";
    static final String NOT_ENOUGH_MANA_MESSAGE_KEY = "message.sparkwitch.riftwalker.shop.not_enough_mana";
    static final String HOTBAR_FULL_MESSAGE_KEY = "message.sparkwitch.riftwalker.shop.hotbar_full";
    static final String UNAVAILABLE_MESSAGE_KEY = "message.sparkwitch.riftwalker.shop.unavailable";
    private static final int HOTBAR_SLOTS = 9;
    private static boolean registered;

    private RiftwalkerShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(RiftwalkerShopService::buildEntries);
        ShopPurchase.BEFORE.register(RiftwalkerShopService::beforePurchase);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!RiftwalkerRules.isRiftwalker(role)) {
            return;
        }
        context.clearEntries();
        AccompliceShopRules.entries().forEach(context::addEntry);
        context.addEntry(gateEntry());
    }

    static ShopEntry gateEntry() {
        return new ShopEntry.Builder(
                RiftwalkerRules.GATE_SHOP_ENTRY_ID,
                SparkWitchItems.riftGate().getDefaultStack(),
                0,
                ShopEntry.Type.TOOL
        ).onBuy(buyer -> buyer instanceof ServerPlayerEntity serverBuyer && buyGate(serverBuyer)).build();
    }

    /**
     * Deny-only: exact role and the gate entry first (everything else returns null untouched), then the readable
     * refusals in {@link #beforeDenial} order (M-6: Wathe would overwrite an {@code onBuy} actionbar line with its
     * generic {@code purchase_failed}, but it shows a BEFORE deny reason as is); never allow. {@code onBuy} stays the
     * authoritative backstop.
     * 只拒绝：先判精确职业与裂隙门商品（其余一律原样返回 null），再按 {@link #beforeDenial} 的顺序给出可读的拒绝原因（M-6：
     * Wathe 会用通用的 {@code purchase_failed} 覆盖 {@code onBuy} 发出的动作栏提示，但会原样显示 BEFORE 的拒绝原因）；从不允许。
     * {@code onBuy} 仍是权威的最后防线。
     */
    private static @Nullable ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry,
                                                                        int index) {
        if (!RiftwalkerRules.GATE_SHOP_ENTRY_ID.equals(entry.id())) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!RiftwalkerRules.isRiftwalker(game.getRole(player))) {
            return null;
        }
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        String denial = beforeDenial(mayBuy(player), gateSlot(player) != null,
                witch.hasManaSystem() && witch.getMana() >= RiftwalkerRules.GATE_MANA_COST);
        return denial == null ? null : ShopPurchase.PurchaseResult.deny(denial);
    }

    /**
     * Pure refusal order shared with {@link #buyGate}: unavailable (not a living, non-spectator participant), then no
     * room in the hotbar, then not enough mana; null = no refusal. Every key takes no argument (Wathe shows deny
     * reasons as plain translations).
     * 与 {@link #buyGate} 一致的纯拒绝顺序：不可购买（非存活且非旁观的参与者）、快捷栏无处可放、魔力不足；null 表示不拒绝。
     * 所有键都不带参数（Wathe 以纯翻译显示拒绝原因）。
     */
    @Nullable
    static String beforeDenial(boolean mayBuy, boolean hasRoom, boolean enoughMana) {
        if (!mayBuy) {
            return UNAVAILABLE_MESSAGE_KEY;
        }
        if (!hasRoom) {
            return HOTBAR_FULL_MESSAGE_KEY;
        }
        return enoughMana ? null : NOT_ENOUGH_MANA_DENY_KEY;
    }

    /**
     * Server purchase on the server thread: role/game/alive → room (a Rift Gate stack with space, else an empty hotbar
     * slot; F-5) → spend 50 mana → insert, refunding the mana if the insert fails. The BEFORE listener already told
     * the player why; these actionbar lines are the backstop for a state change in between.
     * 服务端线程上的购买：职业/对局/存活 → 有位置（优先叠到未满的裂隙门堆，否则空快捷栏格；F-5）→ 扣除 50 魔力 → 放入物品；
     * 放入失败则退还魔力。BEFORE 监听器已经说明了原因；这里的动作栏提示是期间状态变化时的后备。
     */
    static boolean buyGate(ServerPlayerEntity buyer) {
        GameWorldComponent game = GameWorldComponent.KEY.get(buyer.getWorld());
        if (!RiftwalkerRules.isRiftwalker(game.getRole(buyer)) || !mayBuy(buyer)) {
            buyer.sendMessage(Text.translatable(UNAVAILABLE_MESSAGE_KEY), true);
            return false;
        }
        if (gateSlot(buyer) == null) {
            buyer.sendMessage(Text.translatable(HOTBAR_FULL_MESSAGE_KEY), true);
            return false;
        }
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(buyer);
        if (!witch.spendMana(RiftwalkerRules.GATE_MANA_COST)) {
            buyer.sendMessage(Text.translatable(NOT_ENOUGH_MANA_MESSAGE_KEY, RiftwalkerRules.GATE_MANA_COST), true);
            return false;
        }
        if (!insertGate(buyer)) {
            // spendMana only succeeds with mana enabled, so the refund cannot be dropped. / 扣费成功意味着魔力已启用，退还不会丢失。
            witch.addMana(RiftwalkerRules.GATE_MANA_COST);
            buyer.sendMessage(Text.translatable(HOTBAR_FULL_MESSAGE_KEY), true);
            return false;
        }
        return true;
    }

    /** Where a bought gate goes: {@code merge} = add one to the stack in that slot. / 买到的门放在哪：merge 表示叠加。 */
    record GateSlot(int index, boolean merge) {
    }

    /**
     * Pure slot choice (F-5, D4 "unlimited"): the first hotbar slot holding a Rift Gate stack below its max count is
     * merged into; otherwise the first empty hotbar slot (Wathe's {@code insertStackInFreeSlot} order); otherwise none.
     * 纯格子选择（F-5，D4「不限数量」）：优先叠到第一个未满的裂隙门快捷栏堆；否则取第一个空快捷栏格（与 Wathe
     * {@code insertStackInFreeSlot} 的顺序相同）；都没有则为 null。
     */
    @Nullable
    static GateSlot chooseGateSlot(int slots, IntPredicate mergeableGate, IntPredicate empty) {
        for (int slot = 0; slot < slots; slot++) {
            if (mergeableGate.test(slot)) {
                return new GateSlot(slot, true);
            }
        }
        for (int slot = 0; slot < slots; slot++) {
            if (empty.test(slot)) {
                return new GateSlot(slot, false);
            }
        }
        return null;
    }

    private static boolean mayBuy(ServerPlayerEntity buyer) {
        return GameFunctions.isPlayerPlayingAndAlive(buyer) && !buyer.isSpectator();
    }

    @Nullable
    private static GateSlot gateSlot(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack gate = SparkWitchItems.riftGate().getDefaultStack();
        return chooseGateSlot(HOTBAR_SLOTS,
                slot -> isMergeableGate(inventory.getStack(slot), gate),
                slot -> inventory.getStack(slot).isEmpty());
    }

    private static boolean isMergeableGate(ItemStack stack, ItemStack gate) {
        return !stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, gate)
                && stack.getCount() < stack.getMaxCount();
    }

    private static boolean insertGate(PlayerEntity buyer) {
        GateSlot slot = gateSlot(buyer);
        if (slot == null) {
            return false;
        }
        if (slot.merge()) {
            buyer.getInventory().getStack(slot.index()).increment(1);
            return true;
        }
        ItemStack gate = SparkWitchItems.riftGate().getDefaultStack();
        return ShopEntry.insertStackInFreeSlot(buyer, gate);
    }
}
