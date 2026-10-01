package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * Replaces the Insider's shop on both sides with {@link InsiderShopRules#entries}: revolver, crowbar and, when
 * SparkStrength is installed, its tablet. Every entry keeps Wathe's default buy handler so Grand Witch recruitment
 * still refunds at the listed price; there is no Charisma discount. The tablet seam is registry-id only
 * ({@link SparkStrengthTabletCompat}); no SparkStrength class is touched.
 * 在两端把内应商店替换为 {@link InsiderShopRules#entries}：左轮、撬棍，以及安装 SparkStrength 时的平板。
 * 所有条目保留 Wathe 默认购买处理，使大魔女招募仍按标价退款；不接入魅力折扣。平板接缝只按注册 id
 * （{@link SparkStrengthTabletCompat}），不接触任何 SparkStrength 类。
 */
public final class InsiderShopService {
    private static boolean registered;

    private InsiderShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(InsiderShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!InsiderShopRules.rebuildsShopFor(role)) {
            return;
        }
        Optional<Item> tablet = SparkStrengthTabletCompat.tabletItem();
        // SparkTraits entries may already be present; keep them across the rebuild.
        // SparkTraits 条目可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (InsiderShopRules.EntrySpec spec : InsiderShopRules.entries(tablet.isPresent())) {
            ShopEntry entry = switch (spec.kind()) {
                case REVOLVER -> itemEntry(spec, WatheItems.REVOLVER.getDefaultStack(), ShopEntry.Type.WEAPON);
                case CROWBAR -> itemEntry(spec, WatheItems.CROWBAR.getDefaultStack(), ShopEntry.Type.TOOL);
                case TABLET -> tabletEntry(spec, tablet.orElseThrow());
            };
            context.addEntry(entry);
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    private static ShopEntry itemEntry(InsiderShopRules.EntrySpec spec, ItemStack stack, ShopEntry.Type type) {
        return new ShopEntry.Builder(spec.id(), stack, spec.price(), type)
                .stock(spec.stock())
                .build();
    }

    /**
     * The display carries the police-channel description; the bought stack is the plain SparkStrength tablet so
     * SparkStrength sees its own item.
     * 展示栈带警察频道描述；实际发放的是原样的 SparkStrength 平板，SparkStrength 看到的仍是自己的物品。
     */
    private static ShopEntry tabletEntry(InsiderShopRules.EntrySpec spec, Item tablet) {
        ItemStack display = tablet.getDefaultStack();
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(InsiderShopRules.TABLET_DESCRIPTION_KEY)
                        .styled(style -> style.withItalic(false).withColor(InsiderShopRules.DESCRIPTION_COLOR))
        )));
        return new ShopEntry.Builder(spec.id(), display, spec.price(), ShopEntry.Type.TOOL)
                .actualStack(tablet.getDefaultStack())
                .stock(spec.stock())
                .build();
    }
}
