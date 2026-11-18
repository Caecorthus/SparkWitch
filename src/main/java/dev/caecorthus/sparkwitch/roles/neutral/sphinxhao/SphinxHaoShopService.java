package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Sphinx Hao's shop and coin counter. Wathe's shop only knows coins, so this follows 泽渡可可's lightstick (Riftwalker
 * Rift Gate pattern): one 0-coin entry, 拳头子弹, whose {@code onBuy} charges {@link SphinxHaoRules#FIST_AMMO_PRICE}
 * hao on the server atomically with the hotbar insert (refunded if the insert fails), with unlimited stock, and a
 * deny-only {@code ShopPurchase.BEFORE} that explains a refusal and never allows. The list is rebuilt on both sides
 * from the exact synced role only, keeping SparkTraits entries. The ammo item belongs to the GUN work package and is
 * looked up by registry id ({@link SphinxHaoRules#FIST_AMMO_ID}): without it the shop simply offers nothing. The client
 * labels the price in hao through {@code WitchShopClientTexts}.
 * <p>Sphinx Hao earns and holds no coins (owner spec), so {@code CanSeeMoney} answers DENY for it: the coin counter
 * stays hidden and its hao is drawn by its own HUD.
 * 狮身豪的商店与金币计数。Wathe 商店只认金币，因此沿用泽渡可可应援棒（隙行者裂隙门模式）：唯一的 0 金币条目“拳头子弹”，在
 * 服务端的 {@code onBuy} 中扣除 {@link SphinxHaoRules#FIST_AMMO_PRICE} 豪度值，并与放入快捷栏原子执行（放入失败则退还），
 * 库存不限；{@code ShopPurchase.BEFORE} 只负责说明拒绝原因、从不允许。列表在两端只按已同步的精确职业重建，并保留 SparkTraits
 * 条目。子弹物品属于 GUN 工作包，按注册 id（{@link SphinxHaoRules#FIST_AMMO_ID}）查找：缺失时商店不提供任何东西。客户端通过
 * {@code WitchShopClientTexts} 以豪度值显示价格。
 * <p>狮身豪不赚也不持有金币（所有者规格），因此 {@code CanSeeMoney} 对它返回 DENY：金币计数保持隐藏，豪度值由它自己的 HUD
 * 绘制。
 */
public final class SphinxHaoShopService {
    private static final int HOTBAR_SLOTS = 9;
    private static final int DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private SphinxHaoShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SphinxHaoShopService::buildEntries);
        ShopPurchase.BEFORE.register(SphinxHaoShopService::beforePurchase);
        CanSeeMoney.EVENT.register(player -> SphinxHaoParticipation.isSphinxHao(player)
                ? CanSeeMoney.Result.DENY : null);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!SphinxHaoParticipation.isSphinxHao(player)) {
            return;
        }
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        Item ammo = fistAmmo();
        if (ammo != null) {
            context.addEntry(fistAmmoEntry(ammo));
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    /** The GUN package's ammo item, or null while it is not registered. / GUN 包的子弹物品；未注册时为 null。 */
    static @Nullable Item fistAmmo() {
        Item item = Registries.ITEM.get(SphinxHaoRules.FIST_AMMO_ID);
        return item == Items.AIR ? null : item;
    }

    static ShopEntry fistAmmoEntry(Item ammo) {
        return new ShopEntry.Builder(
                SphinxHaoRules.FIST_AMMO_SHOP_ENTRY_ID,
                displayStack(ammo),
                0,
                ShopEntry.Type.WEAPON
        ).onBuy(buyer -> buyer instanceof ServerPlayerEntity serverBuyer && buyFistAmmo(serverBuyer))
                .build();
    }

    /** Blind/Fiend shop precedent: a plain name and one grey, non-italic lore line. / 沿用盲人/魔人商店：普通名称与一行灰色非斜体说明。 */
    private static ItemStack displayStack(Item ammo) {
        ItemStack display = new ItemStack(ammo);
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable(SphinxHaoShopRules.NAME_KEY));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(SphinxHaoShopRules.DESCRIPTION_KEY)
                        .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)))));
        return display;
    }

    /**
     * Deny-only: the fist-ammo entry and the exact role first (everything else returns null untouched), then the
     * readable refusal; never allow. {@code onBuy} stays the authoritative backstop.
     * 只拒绝：先判拳头子弹条目与精确职业（其余一律原样返回 null），再给出可读的拒绝原因；从不允许。{@code onBuy} 仍是权威的
     * 最后防线。
     */
    private static @Nullable ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry,
                                                                        int index) {
        if (!SphinxHaoRules.FIST_AMMO_SHOP_ENTRY_ID.equals(entry.id()) || !SphinxHaoParticipation.isSphinxHao(player)) {
            return null;
        }
        String denial = SphinxHaoShopRules.fistAmmoDenial(mayBuy(player), hasRoom(player),
                SphinxHaoPlayerComponent.KEY.get(player).hao());
        return denial == null ? null : ShopPurchase.PurchaseResult.deny(denial);
    }

    /**
     * Server purchase on the server thread: role and liveness, a free hotbar slot, then the hao; the hao is refunded if
     * the insert fails after all.
     * 服务端线程上的购买：职业与存活、空快捷栏位、然后扣豪度值；放入最终失败时退还豪度值。
     */
    static boolean buyFistAmmo(ServerPlayerEntity buyer) {
        Item ammo = fistAmmo();
        if (ammo == null || !SphinxHaoParticipation.isSphinxHao(buyer)) {
            return false;
        }
        SphinxHaoPlayerComponent state = SphinxHaoPlayerComponent.KEY.get(buyer);
        String denial = SphinxHaoShopRules.fistAmmoDenial(mayBuy(buyer), hasRoom(buyer), state.hao());
        if (denial != null) {
            buyer.sendMessage(Text.translatable(denial), true);
            return false;
        }
        if (!state.spendHao(SphinxHaoRules.FIST_AMMO_PRICE)) {
            return false;
        }
        if (!ShopEntry.insertStackInFreeSlot(buyer, new ItemStack(ammo))) {
            state.addHao(SphinxHaoRules.FIST_AMMO_PRICE);
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
