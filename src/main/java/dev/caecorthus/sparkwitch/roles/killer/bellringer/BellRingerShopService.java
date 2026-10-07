package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Restricts only the Bell Ringer's shop to Wathe's native killer entries, preserving their exact
 * prices, stock, cooldowns, and callbacks. Runs on both sides because Wathe builds shops on both.
 * 仅将敲钟人商店限制为 Wathe 原生杀手条目，并保留其原有价格、库存、冷却与回调；
 * Wathe 在双端构建商店，因此本过滤也在双端执行。
 */
public final class BellRingerShopService {
    /** Entry ids emitted by Wathe's {@code KillerShopBuilder}. / Wathe {@code KillerShopBuilder} 产出的条目 id。 */
    private static final Set<String> RETAINED_NATIVE_ENTRIES = Set.of(
            "knife",
            "revolver",
            "poison_vial",
            "scorpion",
            "lockpick",
            "crowbar",
            "blackout"
    );
    private static boolean registered;

    private BellRingerShopService() {
    }

    /** Idempotent; registers one BuildShopEntries listener. / 幂等；注册一个 BuildShopEntries 监听器。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(BellRingerShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!BellRingerRules.isBellRinger(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        context.getEntries().removeIf(entry -> !RETAINED_NATIVE_ENTRIES.contains(entry.id()));
    }
}
