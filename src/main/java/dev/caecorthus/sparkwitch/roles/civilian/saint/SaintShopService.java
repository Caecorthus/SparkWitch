package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashInventoryRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Saint's first shop, on both sides: one re-buyable Holy Flash (coins shared with Hellfire). Wathe caches stock
 * limits during STARTING (initializeShopsForPlayers), so the gate is the role alone. The default buy handler is kept
 * so Grand Witch recruitment still refunds at shop price; it needs a free hotbar slot and never merges into an
 * existing stack. A deny-only {@code ShopPurchase.BEFORE} listener enforces the carry limit.
 * 圣徒的第一个商店（两端一致）：一个可重复购买的圣光弹（金币与地狱火共用）。Wathe 在 STARTING 阶段
 * （initializeShopsForPlayers）缓存库存上限，因此只按职业判定。保留默认购买处理，使大魔女招募仍按商店价格退款；
 * 默认处理需要一个空的快捷栏位，且从不合并进已有堆叠。只拒绝的 {@code ShopPurchase.BEFORE} 监听器负责携带上限。
 */
public final class SaintShopService {
    /** Shown when the buyer already carries the limit. / 购买者已达携带上限时显示。 */
    public static final String CARRY_LIMIT_KEY = "message.sparkwitch.holy_flash.carry_limit";
    private static boolean registered;

    private SaintShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SaintShopService::buildEntries);
        ShopPurchase.BEFORE.register(SaintShopService::beforePurchase);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SaintRules.isSaint(role)) {
            return;
        }
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        // Re-buyable consumable like Wathe's grenade: no stock and no purchase cooldown; the per-Item use cooldown
        // gates every copy. / 与 Wathe 手雷一样的可重复购买消耗品：无库存、无购买冷却，按物品计算的使用冷却约束每一件。
        context.addEntry(new ShopEntry.Builder(
                HolyFlashRules.SHOP_ENTRY_ID,
                new ItemStack(SparkWitchItems.holyFlash()),
                HolyFlashRules.PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    /**
     * Deny-only carry limit for any shop entry that hands out a Holy Flash; never allows, so other listeners and
     * the default checks still run. Another listener answering first could skip it; the limit is then a soft cap.
     * 对任何发放圣光弹的商店条目执行只拒绝的携带上限；从不允许，因此其他监听器与默认检查照常运行。
     * 若其他监听器先返回结果则可能跳过本检查，此时上限只是软上限。
     */
    private static @Nullable ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry,
                                                                        int index) {
        if (!HolyFlashInventoryRules.isHolyFlash(entry.getActualStack())) {
            return null;
        }
        return HolyFlashInventoryRules.atCarryLimit(HolyFlashInventoryRules.carried(player))
                ? ShopPurchase.PurchaseResult.deny(CARRY_LIMIT_KEY)
                : null;
    }
}
