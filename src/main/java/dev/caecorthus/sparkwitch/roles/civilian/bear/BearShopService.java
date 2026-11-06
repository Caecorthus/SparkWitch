package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import dev.caecorthus.sparkwitch.roles.civilian.bear.headset.CensEarplugsItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShopPurchase;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Replaces the BEAR shop on both sides with {@link BearShopRules#entries()} (the USEC rebuild pattern): SparkTraits
 * entries (e.g. the Impostor revolver) survive the rebuild and every entry passes through the optional SparkTraits
 * Charisma discount. Purchases are server-side ({@code PlayerShopComponent.tryBuy}): the helmet and earplugs land in
 * their worn slot when it is empty (D9), else, like the round's fallback, through a handler-less twin whose default
 * {@code onBuy} carries SparkFactionAPI's second-row wrapper ("nothing fits" returns false and charges nothing). A
 * second helmet or pair of earplugs is refused with a message in {@code ShopPurchase.BEFORE} and again in the handler.
 * 在两端把 BEAR 商店替换为 {@link BearShopRules#entries()}（沿用 USEC 重建方式）：SparkTraits 条目（如内鬼左轮）在重建后保留，
 * 每个条目经过可选的 SparkTraits 魅力折扣。购买在服务端执行（{@code PlayerShopComponent.tryBuy}）：头盔与耳塞在其穿戴槽为空时
 * 直接戴上（D9），否则与榴弹的回退一样交给不带处理器的孪生条目，其默认 {@code onBuy} 带有 SparkFactionAPI 第二行包装（放不下
 * 时返回 false、不扣钱）。已持有头盔或耳塞时，{@code ShopPurchase.BEFORE} 带提示拒绝，处理器中再兜底拒绝一次。
 */
final class BearShopService {
    /** Wathe's head armor slot in {@code PlayerInventory} (36 + 3). / {@code PlayerInventory} 中的头部盔甲槽（36 + 3）。 */
    static final int HEAD_SLOT = 39;

    private static boolean registered;

    private BearShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(BearShopService::buildEntries);
        ShopPurchase.BEFORE.register(BearShopService::denyDuplicate);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!BearShopRules.rebuildsShopFor(role)) {
            return;
        }
        // SparkTraits entries may already be present; keep them across the rebuild. / SparkTraits 条目可能已先加入；重建时保留。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (BearShopRules.EntrySpec spec : BearShopRules.entries()) {
            context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, entry(spec)));
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    /**
     * Both entries of a spec share id, stack, price, type and stock, so the list shape never changes; the handler-less
     * twin is the default insertion every handler falls back to. / 同一规格的两个条目 id、物品、价格、类型与库存相同，
     * 因此列表形状不变；不带处理器的孪生条目是各处理器回退的默认放入。
     */
    private static ShopEntry entry(BearShopRules.EntrySpec spec) {
        ShopEntry defaultEntry = builder(spec).build();
        return builder(spec).onBuy(player -> buy(player, spec.kind(), defaultEntry)).build();
    }

    private static ShopEntry.Builder builder(BearShopRules.EntrySpec spec) {
        ShopEntry.Builder builder = new ShopEntry.Builder(spec.id(), item(spec.kind()).getDefaultStack(), spec.price(),
                spec.type());
        if (spec.hasStockLimit()) {
            builder.stock(spec.stock());
        }
        return builder;
    }

    private static Item item(BearShopRules.EntryKind kind) {
        return switch (kind) {
            case RYS_T -> BearItems.RYS_T;
            case EARPLUGS -> BearItems.EARPLUGS;
            case SMOKE_ROUND -> BearItems.SMOKE_ROUND;
        };
    }

    // ---- Purchase (server) ----

    private static @Nullable ShopPurchase.PurchaseResult denyDuplicate(ServerPlayerEntity player, ShopEntry entry,
                                                                       int index) {
        BearShopRules.EntrySpec spec = BearShopRules.byId(entry.id());
        return spec != null && BearShopRules.refusesPurchase(spec.kind(), owns(player, spec.kind()))
                ? ShopPurchase.PurchaseResult.deny(BearShopRules.ALREADY_OWNED_KEY) : null;
    }

    /** Server-side buy handler; false charges nothing. / 服务端购买处理；返回 false 不扣钱。 */
    static boolean buy(PlayerEntity player, BearShopRules.EntryKind kind, ShopEntry fallback) {
        if (!(player instanceof ServerPlayerEntity buyer)) {
            return false;
        }
        if (kind == BearShopRules.EntryKind.SMOKE_ROUND) {
            return UsecShopPurchase.buyRound(buyer, fallback);
        }
        // Normally already denied with a message by denyDuplicate; this is the authoritative backstop.
        // 通常已由 denyDuplicate 带提示拒绝；此处为权威兜底。
        if (BearShopRules.refusesPurchase(kind, owns(buyer, kind))) {
            return false;
        }
        PlayerInventory inventory = buyer.getInventory();
        if (kind == BearShopRules.EntryKind.RYS_T
                && BearShopRules.landsInWornSlot(kind, inventory.getStack(HEAD_SLOT).isEmpty())) {
            inventory.setStack(HEAD_SLOT, fallback.getActualStack().copy());
            inventory.markDirty();
            buyer.currentScreenHandler.sendContentUpdates();
            return true;
        }
        if (kind == BearShopRules.EntryKind.EARPLUGS
                && BearShopRules.landsInWornSlot(kind, HeadsetSlots.get(buyer).isEmpty())) {
            HeadsetSlots.set(buyer, fallback.getActualStack().copy());
            return true;
        }
        return fallback.onBuy(buyer);
    }

    /**
     * Whether the buyer already owns the one-only gear: inventory 0..40 (armor included), the cursor, the player's own
     * screen slots (2x2 crafting grid) or the headset slot. / 买家是否已持有限购装备：背包 0..40（含盔甲栏）、光标、玩家
     * 自己界面的栏位（2x2 合成格）或耳机槽。
     */
    static boolean owns(ServerPlayerEntity player, BearShopRules.EntryKind kind) {
        Class<?> type = switch (kind) {
            case RYS_T -> RysTHelmetItem.class;
            case EARPLUGS -> CensEarplugsItem.class;
            case SMOKE_ROUND -> null;
        };
        if (type == null) {
            return false;
        }
        if (player.getInventory().contains(stack -> isOf(stack, type))
                || isOf(player.currentScreenHandler.getCursorStack(), type)
                || isOf(HeadsetSlots.get(player), type)) {
            return true;
        }
        for (Slot slot : player.playerScreenHandler.slots) {
            if (isOf(slot.getStack(), type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOf(@Nullable ItemStack stack, Class<?> type) {
        return stack != null && !stack.isEmpty() && type.isInstance(stack.getItem());
    }
}
