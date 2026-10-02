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
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the Riftwalker shop: every plain Accomplice entry, then the Rift Gate (0 gold, unlimited stock, no purchase
 * cooldown, 50 mana charged inside {@code onBuy}). Time Stealer pattern: the list is built from the exact synced role
 * only (never round state, because Wathe caches stock limits during STARTING); {@code ShopPurchase.BEFORE} only
 * explains a mana refusal and never returns {@code allow}, so later listeners (SparkTraits restrictions) still run.
 * The entry costs 0 gold, so SparkTraits Charisma never wraps it. SparkStrength appends its tablet after ours.
 * 构建隙行者商店：先放入普通共犯的全部条目，再加入裂隙门（0 金币、不限库存、无购买冷却，在 {@code onBuy} 中扣除 50 魔力）。
 * 沿用窃时者模式：只按已同步的精确职业构建列表（从不依赖对局状态，因为 Wathe 在 STARTING 阶段缓存库存上限）；
 * {@code ShopPurchase.BEFORE} 只负责说明魔力不足、从不返回允许，因此后续监听器（SparkTraits 限制）仍会运行。
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
     * Deny-only: exact role and the gate entry first (everything else returns null untouched), then a readable mana
     * refusal; never allow. {@code onBuy} stays the authoritative backstop.
     * 只拒绝：先判精确职业与裂隙门商品（其余一律原样返回 null），再给出可读的魔力不足原因；从不允许。
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
        if (!witch.hasManaSystem() || witch.getMana() < RiftwalkerRules.GATE_MANA_COST) {
            return ShopPurchase.PurchaseResult.deny(NOT_ENOUGH_MANA_DENY_KEY);
        }
        return null;
    }

    /**
     * Server purchase on the server thread: role/game/alive → free hotbar slot → spend 50 mana → insert, refunding the
     * mana if the insert fails. Each failure sends a specific actionbar line, because Wathe only shows its generic
     * {@code purchase_failed}.
     * 服务端线程上的购买：职业/对局/存活 → 空快捷栏格 → 扣除 50 魔力 → 放入物品；放入失败则退还魔力。
     * 每种失败都发送具体的动作栏提示，因为 Wathe 只显示通用的 {@code purchase_failed}。
     */
    static boolean buyGate(ServerPlayerEntity buyer) {
        GameWorldComponent game = GameWorldComponent.KEY.get(buyer.getWorld());
        if (!RiftwalkerRules.isRiftwalker(game.getRole(buyer))
                || !GameFunctions.isPlayerPlayingAndAlive(buyer)
                || buyer.isSpectator()) {
            buyer.sendMessage(Text.translatable(UNAVAILABLE_MESSAGE_KEY), true);
            return false;
        }
        if (!hasFreeHotbarSlot(buyer)) {
            buyer.sendMessage(Text.translatable(HOTBAR_FULL_MESSAGE_KEY), true);
            return false;
        }
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(buyer);
        if (!witch.spendMana(RiftwalkerRules.GATE_MANA_COST)) {
            buyer.sendMessage(Text.translatable(NOT_ENOUGH_MANA_MESSAGE_KEY, RiftwalkerRules.GATE_MANA_COST), true);
            return false;
        }
        ItemStack gate = SparkWitchItems.riftGate().getDefaultStack();
        if (!ShopEntry.insertStackInFreeSlot(buyer, gate)) {
            // spendMana only succeeds with mana enabled, so the refund cannot be dropped. / 扣费成功意味着魔力已启用，退还不会丢失。
            witch.addMana(RiftwalkerRules.GATE_MANA_COST);
            buyer.sendMessage(Text.translatable(HOTBAR_FULL_MESSAGE_KEY), true);
            return false;
        }
        return true;
    }

    private static boolean hasFreeHotbarSlot(PlayerEntity player) {
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
