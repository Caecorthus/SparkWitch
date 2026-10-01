package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseShop;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.DisguiseShopSpec;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.doctor4t.wathe.util.ShopUtils;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Replaces a disguised Raven's shop with its adapter whitelist on both sides (outermost @WrapMethod, so it
 * also filters return-value appenders such as SparkStrength's M67/tablet). Both sides read the same acting
 * index answer for the owner, so purchase indexes match; a missing adapter yields an empty shop.
 * 在双端将伪装黑羽鸦的商店替换为适配器白名单（最外层 @WrapMethod，也会过滤 SparkStrength M67/平板等返回值追加）。
 * 双端对拥有者读取相同的扮演索引结果，因此购买下标一致；缺少适配器时商店为空。
 */
@Mixin(value = ShopUtils.class, remap = false)
public abstract class ShopUtilsBlackRavenDisguiseMixin {
    @WrapMethod(method = "getShopEntriesForPlayer")
    private static List<ShopEntry> sparkwitch$applyDisguiseWhitelist(
            PlayerEntity player,
            Operation<List<ShopEntry>> original
    ) {
        List<ShopEntry> entries = original.call(player);
        if (!BlackRavenActingRole.isDisguised(player)) {
            return entries;
        }
        DisguiseShopSpec spec = BlackRavenDisguiseShop.specFor(player);
        return BlackRavenDisguiseShop.finalizeEntries(spec == null ? DisguiseShopSpec.EMPTY : spec, entries);
    }
}
