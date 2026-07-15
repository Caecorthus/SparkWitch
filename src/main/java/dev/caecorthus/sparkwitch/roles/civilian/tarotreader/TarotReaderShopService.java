package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/** Builds the Tarot Reader's three divination-only shop entries. */
public final class TarotReaderShopService {
    private static final int DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private TarotReaderShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(TarotReaderShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!TarotReaderRules.isTarotReader(role)) {
            return;
        }

        context.clearEntries();
        context.addEntry(entry(
                "sparkwitch_tarot_regular",
                SparkWitchItems.tarotCard(),
                TarotReaderRules.REGULAR_PRICE,
                "shop.sparkwitch.tarot.regular",
                "shop.sparkwitch.tarot.regular.description",
                TarotReaderDivinationService::purchaseRegular
        ));
        context.addEntry(entry(
                "sparkwitch_tarot_identity",
                SparkWitchItems.tarotCard(),
                TarotReaderRules.IDENTITY_PRICE,
                "shop.sparkwitch.tarot.identity",
                "shop.sparkwitch.tarot.identity.description",
                TarotReaderDivinationService::purchaseIdentity
        ));
        context.addEntry(entry(
                "sparkwitch_tarot_survival",
                SparkWitchItems.tarotCard(),
                TarotReaderRules.SURVIVAL_PRICE,
                "shop.sparkwitch.tarot.survival",
                "shop.sparkwitch.tarot.survival.description",
                TarotReaderDivinationService::purchaseSurvival
        ));
    }

    private static ShopEntry entry(
            String id,
            Item item,
            int price,
            String nameKey,
            String descriptionKey,
            java.util.function.Predicate<PlayerEntity> onBuy
    ) {
        ItemStack displayStack = item.getDefaultStack();
        displayStack.set(DataComponentTypes.ITEM_NAME, Text.translatable(nameKey));
        displayStack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(descriptionKey)
                        .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR))
        )));
        return new ShopEntry.Builder(id, displayStack, price, ShopEntry.Type.TOOL)
                .onBuy(onBuy)
                .build();
    }
}
