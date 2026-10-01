package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
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
import java.util.Optional;

/**
 * Replaces the Seeker's shop on both sides (camera 150, re-buyable any time with no ownership limit; SparkStrength
 * tablet 50 under SparkStrength's own entry id). Both entries keep Wathe's default buy handler so Grand Witch
 * recruitment still refunds at the listed price, and both pass through the optional SparkTraits Charisma discount.
 * The tablet seam is registry-id only ({@link SparkStrengthTabletCompat}); no SparkStrength class is touched.
 * 在两端替换搜寻者商店（摄像头 150，随时可再买、不限拥有数量；SparkStrength 平板 50，沿用其自身条目 id）。
 * 两个条目都保留 Wathe 默认购买处理，使大魔女招募仍按标价退款，并都经过可选的 SparkTraits 魅力折扣。
 * 平板接缝只按注册 id（{@link SparkStrengthTabletCompat}），不接触任何 SparkStrength 类。
 */
public final class SeekerShopService {
    private static boolean registered;

    private SeekerShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SeekerShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SeekerShopRules.rebuildsShopFor(role)) {
            return;
        }
        Optional<Item> tablet = SparkStrengthTabletCompat.tabletItem();
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (SeekerShopRules.EntrySpec spec : SeekerShopRules.entries(tablet.isPresent())) {
            ShopEntry entry = switch (spec.kind()) {
                case CAMERA -> cameraEntry(spec);
                case TABLET -> tabletEntry(spec, tablet.orElseThrow());
            };
            context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, entry));
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    private static ShopEntry cameraEntry(SeekerShopRules.EntrySpec spec) {
        // No stock and no ownership limit: any number of cameras, only money limits it.
        // 不设库存也不限拥有数量：摄像头数量不限，只受金钱限制。
        return new ShopEntry.Builder(spec.id(), SparkWitchItems.seekerCamera().getDefaultStack(), spec.price(),
                ShopEntry.Type.TOOL).build();
    }

    /**
     * The display carries the Seeker description; the bought stack is the plain SparkStrength tablet so SparkStrength
     * sees its own item. Stock 1, like SparkStrength's own entry.
     * 展示栈带搜寻者描述；实际发放的是原样的 SparkStrength 平板，SparkStrength 看到的仍是自己的物品。库存 1，与其自身条目一致。
     */
    private static ShopEntry tabletEntry(SeekerShopRules.EntrySpec spec, Item tablet) {
        ItemStack display = tablet.getDefaultStack();
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(SeekerShopRules.TABLET_DESCRIPTION_KEY)
                        .styled(style -> style.withItalic(false).withColor(SeekerShopRules.DESCRIPTION_COLOR))
        )));
        return new ShopEntry.Builder(spec.id(), display, spec.price(), ShopEntry.Type.TOOL)
                .actualStack(tablet.getDefaultStack())
                .stock(spec.stock())
                .build();
    }
}
