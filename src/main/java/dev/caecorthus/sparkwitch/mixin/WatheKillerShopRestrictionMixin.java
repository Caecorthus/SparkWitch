package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.doctor4t.wathe.util.ShopUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Retires three Wathe killer-shop entries (owner request, 2026-11-08): Psycho Mode, Poison Vial and Scorpion.
 * Wraps the final list, so role-owned shop rebuilds cannot reintroduce them. Applies to every player's shop.
 * 按所有者要求（2026-11-08）下架商店中的疯魔模式、毒药瓶与蝎子。包裹最终列表，职业自建商店也无法重新加入；
 * 对所有玩家的商店生效。
 */
@Mixin(value = ShopUtils.class, remap = false)
public abstract class WatheKillerShopRestrictionMixin {
    private static final Set<String> REMOVED_ENTRY_IDS = Set.of("psycho_mode", "poison_vial", "scorpion");

    @WrapMethod(method = "getShopEntriesForPlayer")
    private static List<ShopEntry> sparkwitch$removeRetiredKillerEntries(
            PlayerEntity player,
            Operation<List<ShopEntry>> original
    ) {
        List<ShopEntry> entries = new ArrayList<>(original.call(player));
        entries.removeIf(entry -> REMOVED_ENTRY_IDS.contains(entry.id()));
        return entries;
    }
}
