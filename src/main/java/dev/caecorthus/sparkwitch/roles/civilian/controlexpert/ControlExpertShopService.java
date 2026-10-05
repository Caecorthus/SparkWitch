package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Replaces the Control Expert's shop on both sides. Wathe caches stock limits during STARTING
 * (initializeShopsForPlayers), so the gate is the role alone; a running-state gate would make stock(1) unlimited.
 * 在两端替换控场专家的商店。Wathe 在 STARTING 阶段（initializeShopsForPlayers）缓存库存上限，因此只按职业判定；
 * 若依赖对局运行状态，stock(1) 会变成不限量。
 */
public final class ControlExpertShopService {
    private static boolean registered;

    private ControlExpertShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(ControlExpertShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!ControlExpertRules.isControlExpert(role)) {
            return;
        }
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                ControlExpertRules.DISRUPTOR_ENTRY_ID,
                SparkWitchItems.disruptor().getDefaultStack(),
                ControlExpertRules.DISRUPTOR_PRICE,
                ShopEntry.Type.TOOL
        ).stock(1).build());
        context.addEntry(new ShopEntry.Builder(
                ControlExpertRules.TASER_ENTRY_ID,
                SparkWitchItems.taser().getDefaultStack(),
                ControlExpertRules.TASER_PRICE,
                ShopEntry.Type.WEAPON
        ).stock(1).build());
        // Re-buyable consumable like Wathe's grenade: no stock and no purchase cooldown; the per-Item use cooldown
        // gates every copy. / 与 Wathe 手雷一样的可重复购买消耗品：无库存、无购买冷却，按物品计算的使用冷却约束每一件。
        context.addEntry(new ShopEntry.Builder(
                ControlExpertRules.SHOCK_DEVICE_ENTRY_ID,
                SparkWitchItems.shockDevice().getDefaultStack(),
                ControlExpertRules.SHOCK_DEVICE_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }
}
