package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Koko's shop: one lightstick, paid in traffic, one per round (owner 2026-11-13: no knife, no lockpick). Wathe's shop
 * only knows coins, so this follows the Riftwalker Rift Gate pattern: a 0-coin entry whose {@code onBuy} charges the
 * traffic on the server, atomically with the hotbar insert, and a deny-only {@code ShopPurchase.BEFORE} that explains
 * a refusal and never allows, so later listeners still run. The list is built on both sides from the exact synced role
 * only (Wathe caches stock limits during STARTING). The client labels the price through {@code WitchShopClientTexts}.
 * <p>She earns and holds no coins, so {@code CanSeeMoney} answers DENY for her: the coin counter stays hidden and her
 * traffic is drawn by her own HUD. SparkTraits entries that were added first are kept across the rebuild.
 * 可可的商店：只卖一根应援棒，以流量支付，每局限购一根（所有者 2026-11-13：不卖刀和撬锁器）。Wathe 的商店只认金币，因此沿用
 * 隙行者裂隙门的做法：0 金币条目，在服务端的 {@code onBuy} 中扣除流量，并与放入快捷栏原子执行；{@code ShopPurchase.BEFORE}
 * 只负责说明拒绝原因、从不返回允许，后续监听器仍会运行。列表在两端只按已同步的精确职业构建（Wathe 在 STARTING 阶段缓存库存
 * 上限）。客户端通过 {@code WitchShopClientTexts} 显示价格。
 * <p>她不赚也不持有金币，因此 {@code CanSeeMoney} 对她返回 DENY：金币计数保持隐藏，流量由她自己的 HUD 绘制。此前已加入的
 * SparkTraits 条目在重建时保留。
 */
public final class KokoShopService {
    private static final int HOTBAR_SLOTS = 9;
    private static boolean registered;

    private KokoShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(KokoShopService::buildEntries);
        ShopPurchase.BEFORE.register(KokoShopService::beforePurchase);
        CanSeeMoney.EVENT.register(player -> KokoParticipation.isKoko(player) ? CanSeeMoney.Result.DENY : null);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!KokoParticipation.isKoko(player)) {
            return;
        }
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(lightstickEntry());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    static ShopEntry lightstickEntry() {
        return new ShopEntry.Builder(
                KokoRules.LIGHTSTICK_SHOP_ENTRY_ID,
                SparkWitchItems.kokoLightstick().getDefaultStack(),
                0,
                ShopEntry.Type.TOOL
        ).stock(KokoRules.LIGHTSTICK_STOCK)
                .onBuy(buyer -> buyer instanceof ServerPlayerEntity serverBuyer && buyLightstick(serverBuyer))
                .build();
    }

    /**
     * Deny-only: her lightstick entry and her exact role first (everything else returns null untouched), then the
     * readable refusal; never allow. {@code onBuy} stays the authoritative backstop.
     * 只拒绝：先判应援棒条目与精确职业（其余一律原样返回 null），再给出可读的拒绝原因；从不允许。{@code onBuy} 仍是权威的
     * 最后防线。
     */
    private static @Nullable ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry,
                                                                        int index) {
        if (!KokoRules.LIGHTSTICK_SHOP_ENTRY_ID.equals(entry.id()) || !KokoParticipation.isKoko(player)) {
            return null;
        }
        String denial = KokoRules.lightstickDenial(mayBuy(player), hasRoom(player),
                KokoComponent.KEY.get(player).traffic());
        return denial == null ? null : ShopPurchase.PurchaseResult.deny(denial);
    }

    /**
     * Server purchase on the server thread: role and liveness, a free hotbar slot, then the traffic; the traffic is
     * refunded if the insert fails after all.
     * 服务端线程上的购买：职业与存活、快捷栏空位、然后扣流量；放入最终失败时退还流量。
     */
    static boolean buyLightstick(ServerPlayerEntity buyer) {
        if (!KokoParticipation.isKoko(buyer)) {
            return false;
        }
        KokoComponent state = KokoComponent.KEY.get(buyer);
        String denial = KokoRules.lightstickDenial(mayBuy(buyer), hasRoom(buyer), state.traffic());
        if (denial != null) {
            buyer.sendMessage(Text.translatable(denial), true);
            return false;
        }
        if (!state.spendTraffic(KokoRules.LIGHTSTICK_PRICE)) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(buyer, new ItemStack(SparkWitchItems.kokoLightstick()))) {
            state.addTraffic(KokoRules.LIGHTSTICK_PRICE);
            return false;
        }
        return true;
    }

    private static boolean mayBuy(ServerPlayerEntity buyer) {
        return GameFunctions.isPlayerPlayingAndAlive(buyer) && !buyer.isSpectator();
    }

    private static boolean hasRoom(PlayerEntity player) {
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
