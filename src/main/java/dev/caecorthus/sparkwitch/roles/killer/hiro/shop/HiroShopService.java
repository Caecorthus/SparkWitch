package dev.caecorthus.sparkwitch.roles.killer.hiro.shop;

import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Replaces 二阶堂希罗's native killer shop with the five rows of {@link HiroShopRules} (Ninja / Murderous Witch
 * pattern: clear, then add). Gated on the raw role and registered in the DEFAULT phase like every other killer shop, so
 * SparkTraits' {@code sparktraits:charisma_discount} phase (ordered after DEFAULT) still discounts the rows. Clearing
 * also removes the native {@code grenade} row, so SparkStrength never appends its M67 for her. Runs on both sides, so
 * purchase indexes match.
 * 用 {@link HiroShopRules} 的五个条目替换二阶堂希罗的原生杀手商店（忍者、杀意魔女模式：先清空再添加）。按真实身份判定，与其他
 * 杀手商店一样注册在默认阶段，因此排在其后的 SparkTraits {@code sparktraits:charisma_discount} 阶段仍会为这些条目打折。清空同时
 * 移除了原生 {@code grenade} 条目，SparkStrength 因而不会为她追加 M67。两端都会执行，购买下标保持一致。
 */
public final class HiroShopService {
    private static boolean registered;

    private HiroShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(HiroShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!HiroRules.isHiro(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        context.clearEntries();
        for (HiroShopRules.PlannedEntry planned : HiroShopRules.plannedEntries()) {
            ShopEntry.Builder builder = new ShopEntry.Builder(
                    planned.id(),
                    new ItemStack(Registries.ITEM.get(planned.item())),
                    planned.price(),
                    planned.type()
            );
            if (planned.maxStock() > 0) {
                builder.stock(planned.maxStock());
            }
            context.addEntry(builder.build());
        }
    }
}
