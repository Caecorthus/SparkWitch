package dev.caecorthus.sparkwitch.compat.recruitment;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Recognizes the public facade's price-only wrapper without naming or reflecting any Traits internals.
 * 通过公共门面识别纯价格包装，不命名或反射任何 Traits 内部实现；缺少门面时保守退化。 */
final class SparkTraitsRecruitmentBridge {
    private static final @Nullable Method DISCOUNT = resolveDiscount();

    private SparkTraitsRecruitmentBridge() { }

    static @Nullable Class<?> charismaWrapperType(PlayerEntity player) {
        if (DISCOUNT == null) return null;
        try {
            ShopEntry probe = new ShopEntry(ItemStack.EMPTY, 100, ShopEntry.Type.TOOL);
            Object discounted = DISCOUNT.invoke(null, player, probe);
            return discounted instanceof ShopEntry entry && entry != probe ? entry.getClass() : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable Method resolveDiscount() {
        try {
            return Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi")
                    .getMethod("discountShopEntryForCharisma", PlayerEntity.class, ShopEntry.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }
}
