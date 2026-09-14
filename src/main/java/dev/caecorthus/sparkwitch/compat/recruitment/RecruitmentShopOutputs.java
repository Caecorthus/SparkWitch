package dev.caecorthus.sparkwitch.compat.recruitment;

import dev.doctor4t.wathe.util.ShopEntry;
import dev.doctor4t.wathe.util.ShopUtils;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.GrandWitchRecruitmentRules.Price;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

/** Reads real output provenance while the effective shop is built, never executing a purchase.
 * 构建有效商店时记录真实产物来源，绝不执行购买回调或把技能图标当作商品。 */
public final class RecruitmentShopOutputs {
    // Weak keys retain metadata for cached default offers without retaining transient shop rebuilds.
    // 弱键为缓存的默认商品保留元数据，不持有临时商店重建对象。
    private static final Map<ShopEntry, List<ItemStack>> OUTPUTS = new WeakHashMap<>();

    private RecruitmentShopOutputs() { }

    public static Map<Item, List<Price>> pricesFor(ServerPlayerEntity player) {
        // Includes existing BuildShopEntries discounts, without applying charisma twice.
        // 包含现有商店事件的折扣，不会二次应用魅力折扣。
        List<ShopEntry> entries = ShopUtils.getShopEntriesForPlayer(player);
        Class<?> charismaWrapper = SparkTraitsRecruitmentBridge.charismaWrapperType(player);
        Map<Item, List<Price>> prices = new HashMap<>();
        synchronized (OUTPUTS) {
            for (ShopEntry entry : entries) {
                if (entry.price() < 0 || (entry.getClass() != ShopEntry.class
                        && entry.getClass() != charismaWrapper)) continue;
                for (ItemStack output : OUTPUTS.getOrDefault(entry, List.of())) {
                    if (!output.isEmpty()) {
                        prices.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>())
                                .add(new Price(entry.price(), output.getCount()));
                    }
                }
            }
        }
        return prices;
    }

    /** Constructor-only Wathe seam; no optional mod internals are inspected.
     * 只观察 Wathe 构造参数；不访问可选模组内部类、字段或购买回调。 */
    public static void observe(ShopEntry entry, boolean customHandler) {
        synchronized (OUTPUTS) {
            observeLocked(entry, customHandler);
        }
    }

    private static void observeLocked(ShopEntry entry, boolean customHandler) {
        var outputs = OUTPUTS;
        List<ItemStack> physical = List.of();
        if (entry.getClass() == ShopEntry.class) {
            if (!customHandler) {
                physical = List.of(entry.getActualStack());
            } else if ("waiter_random_food_or_drink".equals(entry.id())) {
                physical = waiterOutputs();
            }
        } else {
            // Charisma wrappers forward the exact original stack objects. Preserve the original
            // provenance (including an empty skill output), not the wrapper's fallback icon.
            // 魅力包装复用原始堆栈对象；继承真实产物（技能为空），而非包装层的图标兜底。
            for (var original : outputs.entrySet()) {
                ShopEntry delegate = original.getKey();
                if (delegate.getClass() == ShopEntry.class && entry.id().equals(delegate.id())
                        && entry.displayStack() == delegate.displayStack()
                        && entry.getActualStack() == delegate.getActualStack()) {
                    physical = original.getValue();
                    break;
                }
            }
        }
        outputs.put(entry, physical);
    }

    private static List<ItemStack> waiterOutputs() {
        // Audited against noellesroles-1.7.6-h1.5.6-spark.jar, one random output per purchase.
        // 已核对锁定 1.7.6 JAR；每次购买随机产生其中一件，而非整包十件。
        return List.of(WatheItems.OLD_FASHIONED.getDefaultStack(), WatheItems.MOJITO.getDefaultStack(),
                WatheItems.MARTINI.getDefaultStack(), WatheItems.COSMOPOLITAN.getDefaultStack(),
                WatheItems.CHAMPAGNE.getDefaultStack(), Items.BREAD.getDefaultStack(),
                Items.COOKED_BEEF.getDefaultStack(), Items.COOKED_PORKCHOP.getDefaultStack(),
                Items.BAKED_POTATO.getDefaultStack(), Items.APPLE.getDefaultStack());
    }
}
