package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Rebuilds the Ferryman's shop on both sides: the crowbar (25 gold) and the stock-1 Soul Moment (250 gold, started by
 * {@link FerrymanMomentService#tryStart}, which charges nothing when it refuses). Purchases resolve by list index, so
 * the list depends on the synced role alone; SparkTraits entries already present are preserved across the rebuild, as
 * the Fiend's shop does.
 * 两端重建摆渡人商店：撬棍（25 金币）与库存 1 的「亡魂时刻」（250 金币，由 {@link FerrymanMomentService#tryStart} 开启，
 * 拒绝时不扣费）。购买按列表下标解析，因此列表只依赖已同步的职业；重建时保留已存在的 SparkTraits 条目，与魔人商店相同。
 */
public final class FerrymanShopService {
    private static final String CROWBAR_NAME_KEY = "shop.sparkwitch.ferryman.crowbar";
    private static final String CROWBAR_DESCRIPTION_KEY = "shop.sparkwitch.ferryman.crowbar.description";
    private static final String MOMENT_NAME_KEY = "shop.sparkwitch.ferryman.moment";
    private static final String MOMENT_DESCRIPTION_KEY = "shop.sparkwitch.ferryman.moment.description";
    /**
     * Second LORE line: vanilla tooltips never wrap, and one line overflowed a 1080p screen at the default GUI scale.
     * 第二行 LORE：原版提示框从不换行，单行在 1080p 默认 GUI 缩放下会超出屏幕。
     */
    private static final String MOMENT_DESCRIPTION_LINE2_KEY = "shop.sparkwitch.ferryman.moment.description.line2";
    private static final String INVENTORY_FULL_KEY = "message.sparkwitch.ferryman.shop.inventory_full";
    private static final int DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private FerrymanShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(FerrymanShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!FerrymanParticipation.isFerryman(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        List<ShopEntry> preserved = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(crowbarEntry());
        context.addEntry(momentEntry());
        SparkTraitsShopEntryPreserver.restore(context, preserved);
    }

    static ShopEntry momentEntry() {
        ItemStack display = new ItemStack(Items.SOUL_LANTERN);
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable(MOMENT_NAME_KEY));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(MOMENT_DESCRIPTION_KEY)
                        .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)),
                Text.translatable(MOMENT_DESCRIPTION_LINE2_KEY)
                        .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)))));
        return new ShopEntry.Builder(FerrymanRules.MOMENT_SHOP_ENTRY_ID, display, FerrymanRules.MOMENT_PRICE,
                ShopEntry.Type.TOOL)
                .stock(1)
                .onBuy(FerrymanMomentService::tryStart)
                .build();
    }

    static ShopEntry crowbarEntry() {
        ItemStack display = new ItemStack(WatheItems.CROWBAR);
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable(CROWBAR_NAME_KEY));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable(CROWBAR_DESCRIPTION_KEY)
                .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)))));
        return new ShopEntry.Builder(FerrymanRules.CROWBAR_SHOP_ENTRY_ID, display, FerrymanRules.CROWBAR_PRICE,
                ShopEntry.Type.TOOL)
                .stock(1)
                .onBuy(FerrymanShopService::buyCrowbar)
                .build();
    }

    /**
     * Server-side buy handler ({@code PlayerShopComponent.tryBuy}); false charges nothing. The crowbar is a real
     * inventory stack, so a full inventory refuses the purchase instead of dropping it on the floor.
     * 服务端购买处理（{@code PlayerShopComponent.tryBuy}）；返回 false 不扣费。撬棍是真实背包物品，背包满时拒绝购买，
     * 而不是把物品掉在地上。
     */
    private static boolean buyCrowbar(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity buyer) || !FerrymanParticipation.isActiveFerryman(buyer)) {
            return false;
        }
        if (!buyer.getInventory().insertStack(new ItemStack(WatheItems.CROWBAR))) {
            buyer.sendMessage(Text.translatable(INVENTORY_FULL_KEY), true);
            return false;
        }
        buyer.currentScreenHandler.sendContentUpdates();
        return true;
    }
}
