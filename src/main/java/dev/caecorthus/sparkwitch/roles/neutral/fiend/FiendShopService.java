package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Replaces the Fiend's shop with the single Fiend Moment entry on both sides. Purchases resolve by list index, so the
 * list depends on the synced role alone; Wathe caches stock limits during STARTING, so a running-state gate would make
 * stock(1) unlimited. The display is a vanilla item and never {@code wathe:grenade}, so SparkStrength's M67 append (it
 * requires a grenade entry) stays out. No SparkTraits Charisma discount: the price is a win condition.
 * 在两端将魔人商店替换为唯一的「魔人时刻」条目。购买按列表下标解析，因此列表只依赖已同步的职业；Wathe 在 STARTING
 * 阶段缓存库存上限，若依赖对局运行状态，stock(1) 会变成不限量。展示物品为原版物品，绝非 {@code wathe:grenade}，
 * 因此 SparkStrength 的 M67 追加（需要手雷条目）不会出现。不享受 SparkTraits 魅力折扣：该价格是胜利条件。
 */
public final class FiendShopService {
    static final String NAME_KEY = "shop.sparkwitch.fiend.moment";
    static final String DESCRIPTION_KEY = "shop.sparkwitch.fiend.moment.description";
    private static final int DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private FiendShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(FiendShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!FiendParticipation.isFiendRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(momentEntry());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    static ShopEntry momentEntry() {
        ItemStack display = new ItemStack(Items.WITHER_SKELETON_SKULL);
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable(NAME_KEY));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable(DESCRIPTION_KEY)
                .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)))));
        return new ShopEntry.Builder(FiendRules.MOMENT_SHOP_ENTRY_ID, display, FiendRules.MOMENT_PRICE,
                ShopEntry.Type.TOOL)
                .stock(1)
                .onBuy(FiendMomentService::tryStart)
                .build();
    }
}
