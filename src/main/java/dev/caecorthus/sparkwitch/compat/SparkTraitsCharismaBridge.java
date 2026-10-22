package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * SparkTraits 魅力折扣的软兼容桥。
 *
 * <p>外部只给我们一个玩家和一个商店条目；如果 SparkTraits 可用，就把条目交给它的公共 API
 * 套用一次【魅力】折扣。若模组缺失或旧版没有这个 API，就原样返回条目。</p>
 */
public final class SparkTraitsCharismaBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final OptionalStaticMethod DISCOUNT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
            "discountShopEntryForCharisma", PlayerEntity.class, ShopEntry.class).initializingClass();

    private SparkTraitsCharismaBridge() {
    }

    public static @Nullable ShopEntry discountShopEntry(@Nullable PlayerEntity player, @Nullable ShopEntry entry) {
        if (player == null || entry == null) {
            return entry;
        }
        // Absent, missing, failing or non-entry answers all keep the original entry. / 缺失、失败或非条目结果均保留原条目。
        return DISCOUNT.callOrNull(player, entry) instanceof ShopEntry discounted ? discounted : entry;
    }
}
