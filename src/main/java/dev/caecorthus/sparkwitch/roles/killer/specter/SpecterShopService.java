package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * The Specter's role shop: one ghost-trigger entry, 50 coins, poison-style frame, inserted between the shared shop's
 * weapon/poison group and its tool group. Registered in the DEFAULT phase like every other killer shop (Hunter, Ninja,
 * Bell Ringer, Time Stealer, Witch Maiden): Wathe seeds the base entries into the context before the event runs, so
 * the insertion point exists, and SparkTraits' {@code sparktraits:charisma_discount} phase (ordered after DEFAULT)
 * still discounts this entry for a Charisma Specter.
 * 幽魂职业商店：一个鬼魂触发器条目，50 金币，毒药类边框，插在通用商店的武器/毒药组与工具组之间。与其他杀手商店（猎人、忍者、
 * 敲钟人、窃时者、巫女）一样注册在默认阶段：Wathe 在事件运行前已把基础条目放入上下文，因此插入点已存在；SparkTraits 排在默认
 * 阶段之后的 {@code sparktraits:charisma_discount} 阶段仍会为拥有魅力的幽魂打折本条目。
 */
public final class SpecterShopService {
    public static final String ENTRY_ID = "ghost_trigger";
    private static final int PRICE = 50;
    private static boolean registered;

    private SpecterShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SpecterShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!SpecterRules.isSpecter(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        ShopEntry entry = new ShopEntry.Builder(
                ENTRY_ID,
                new ItemStack(SparkWitchItems.ghostTrigger()),
                PRICE,
                ShopEntry.Type.POISON
        ).build();
        context.addEntry(toolGroupIndex(context), entry);
    }

    /** First tool entry, i.e. the seam after weapons/poisons; the end when no tool entry exists. / 第一个工具条目。 */
    private static int toolGroupIndex(BuildShopEntries.ShopContext context) {
        for (int index = 0; index < context.size(); index++) {
            if (context.getEntry(index).type() == ShopEntry.Type.TOOL) {
                return index;
            }
        }
        return context.size();
    }
}
