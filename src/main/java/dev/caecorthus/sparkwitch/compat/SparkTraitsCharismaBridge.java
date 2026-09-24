package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * SparkTraits 魅力折扣的软兼容桥。
 *
 * <p>外部只给我们一个玩家和一个商店条目；如果 SparkTraits 可用，就把条目交给它的公共 API
 * 套用一次【魅力】折扣。若模组缺失或旧版没有这个 API，就原样返回条目。</p>
 */
public final class SparkTraitsCharismaBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    private static volatile Method discountMethod;
    private static volatile boolean lookupFailed;

    private SparkTraitsCharismaBridge() {
    }

    public static @Nullable ShopEntry discountShopEntry(@Nullable PlayerEntity player, @Nullable ShopEntry entry) {
        if (player == null || entry == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return entry;
        }
        Method method = discountMethod();
        if (method == null) {
            return entry;
        }
        try {
            Object result = method.invoke(null, player, entry);
            return result instanceof ShopEntry discounted ? discounted : entry;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return entry;
        }
    }

    @Nullable
    private static Method discountMethod() {
        if (lookupFailed) {
            return null;
        }
        Method cached = discountMethod;
        if (cached != null) {
            return cached;
        }
        synchronized (SparkTraitsCharismaBridge.class) {
            if (discountMethod != null) {
                return discountMethod;
            }
            if (lookupFailed) {
                return null;
            }
            try {
                Class<?> apiClass = Class.forName(API_CLASS);
                discountMethod = apiClass.getMethod("discountShopEntryForCharisma", PlayerEntity.class, ShopEntry.class);
                return discountMethod;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                lookupFailed = true;
                return null;
            }
        }
    }
}
