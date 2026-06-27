package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Builds the Grand Witch's mana shop without granting native killer shop access.
 * 构建大魔女专属魔力商店，不借用原生杀手商店权限。
 */
public final class GrandWitchShopService {
    private static final int SHOP_DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private GrandWitchShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(GrandWitchShopService::buildEntries);
        ShopPurchase.BEFORE.register(GrandWitchShopService::beforePurchase);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!GrandWitchRules.isGrandWitch(role)) {
            return;
        }

        context.clearEntries();
        context.addEntry(new ShopEntry.Builder("lockpick", WatheItems.LOCKPICK.getDefaultStack(), 50, ShopEntry.Type.TOOL)
                .stock(1)
                .build());
        context.addEntry(new ShopEntry.Builder("poison_vial", WatheItems.POISON_VIAL.getDefaultStack(), 100, ShopEntry.Type.POISON)
                .build());
        context.addEntry(new ShopEntry.Builder("scorpion", WatheItems.SCORPION.getDefaultStack(), 100, ShopEntry.Type.POISON)
                .build());
        context.addEntry(spellEntry(
                GrandWitchRules.GrandWitchSpell.OBSCURE,
                ShopEntry.Type.TOOL
        ));
        context.addEntry(spellEntry(
                GrandWitchRules.GrandWitchSpell.BLINDNESS,
                ShopEntry.Type.TOOL
        ));
        context.addEntry(spellEntry(
                GrandWitchRules.GrandWitchSpell.FEAR,
                ShopEntry.Type.TOOL
        ));
        context.addEntry(spellEntry(
                GrandWitchRules.GrandWitchSpell.HEAVINESS,
                ShopEntry.Type.TOOL
        ));
    }

    private static ShopPurchase.PurchaseResult beforePurchase(ServerPlayerEntity player, ShopEntry entry, int index) {
        GrandWitchRules.GrandWitchSpell spell = GrandWitchRules.GrandWitchSpell.fromEntryId(entry.id());
        if (spell == null) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!GrandWitchRules.isGrandWitch(role)) {
            return ShopPurchase.PurchaseResult.deny("shop.error.purchase_denied");
        }
        if (!GrandWitchSpellService.hasEnoughMana(player, spell)) {
            return ShopPurchase.PurchaseResult.deny("shop.error.sparkwitch.not_enough_mana");
        }
        return ShopPurchase.PurchaseResult.allow(0);
    }

    private static ShopEntry spellEntry(
            GrandWitchRules.GrandWitchSpell spell,
            ShopEntry.Type type
    ) {
        return new ShopEntry.Builder(spell.entryId(), displayStackForSpell(spell), 0, type)
                .cooldown(spell.cooldownTicks())
                .onBuy(player -> player instanceof ServerPlayerEntity serverPlayer
                        && GrandWitchSpellService.cast(serverPlayer, spell))
                .build();
    }

    static ItemStack displayStackForSpell(GrandWitchRules.GrandWitchSpell spell) {
        ItemStack stack = displayItemForSpell(spell).getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable(spell.translationKey()));
        // LimitedInventoryScreen renders display-stack lore as the lower shop description.
        // LimitedInventoryScreen 会把展示物品的 lore 渲染为商店下方的功能描述。
        stack.set(DataComponentTypes.LORE, descriptionLoreForSpell(spell));
        return stack;
    }

    static LoreComponent descriptionLoreForSpell(GrandWitchRules.GrandWitchSpell spell) {
        return new LoreComponent(List.of(descriptionText(spell)));
    }

    private static Text descriptionText(GrandWitchRules.GrandWitchSpell spell) {
        return Text.translatable(spell.descriptionTranslationKey())
                .styled(style -> style.withItalic(false).withColor(SHOP_DESCRIPTION_COLOR));
    }

    private static Item displayItemForSpell(GrandWitchRules.GrandWitchSpell spell) {
        return Registries.ITEM.get(displayItemIdForSpell(spell));
    }

    static Identifier displayItemIdForSpell(GrandWitchRules.GrandWitchSpell spell) {
        return switch (spell) {
            case OBSCURE -> Identifier.ofVanilla("barrier");
            case BLINDNESS -> Identifier.ofVanilla("ender_pearl");
            case FEAR -> Identifier.ofVanilla("soul_lantern");
            case HEAVINESS -> Identifier.ofVanilla("anvil");
        };
    }
}
