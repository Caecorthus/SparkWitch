package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperKnockoutService;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 给外部模组反射调用的稳定入口。
 *
 * <p>当前主要服务 SparkStrength 验尸官伪装为绑架者的场景；外部只需反射本类方法，
 * 不需要在编译期依赖 SparkWitch。</p>
 */
public final class SparkWitchKidnapperCompat {
    private SparkWitchKidnapperCompat() {
    }

    public static ItemStack createKnockoutDrugStack() {
        return SparkWitchItems.knockoutDrug().getDefaultStack();
    }

    public static ShopEntry createKnockoutDrugShopEntry() {
        return KidnapperKnockoutService.shopEntry();
    }

    public static ShopEntry createKnockoutDrugShopEntry(PlayerEntity player) {
        return KidnapperKnockoutService.shopEntry(player);
    }
}
