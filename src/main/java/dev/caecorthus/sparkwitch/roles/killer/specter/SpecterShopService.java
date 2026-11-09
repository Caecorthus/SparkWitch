package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * The Specter's role shop: one ghost-trigger entry, 50 coins, poison-style frame, inserted between the shared shop's
 * weapon/poison group and its tool group. Registered in a phase after the default builder, so the base weapons and
 * tools already exist when the insertion point is chosen.
 * 幽魂职业商店：一个鬼魂触发器条目，50 金币，毒药类边框，插在通用商店的武器/毒药组与工具组之间。
 * 注册在默认构建器之后的阶段，选择插入点时基础武器和工具已经就位。
 */
public final class SpecterShopService {
    public static final String ENTRY_ID = "ghost_trigger";
    private static final int PRICE = 50;
    private static final Identifier SHOP_PHASE = SparkWitch.id("specter_shop_entries");
    private static boolean registered;

    private SpecterShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, SHOP_PHASE);
        BuildShopEntries.EVENT.register(SHOP_PHASE, SpecterShopService::buildEntries);
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
