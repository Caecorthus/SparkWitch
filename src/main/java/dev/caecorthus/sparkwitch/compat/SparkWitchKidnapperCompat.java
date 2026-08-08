package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperKnockoutService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * 给外部模组反射调用的稳定入口。
 *
 * <p>当前主要服务 SparkStrength 验尸官伪装为绑架者的场景；外部只需反射本类方法，
 * 不需要在编译期依赖 SparkWitch。</p>
 */
public final class SparkWitchKidnapperCompat {
    public static final Identifier KIDNAPPER_ROLE_ID = KidnapperRules.ROLE_ID;

    private SparkWitchKidnapperCompat() {
    }

    public static Item knockoutDrugItem() {
        return SparkWitchItems.knockoutDrug();
    }

    public static ItemStack createKnockoutDrugStack() {
        return SparkWitchItems.knockoutDrug().getDefaultStack();
    }

    public static ItemStack createKnockoutDrugDisplayStack() {
        return KidnapperKnockoutService.displayStack();
    }

    public static ShopEntry createKnockoutDrugShopEntry() {
        return KidnapperKnockoutService.shopEntry();
    }

    public static ShopEntry createKnockoutDrugShopEntry(PlayerEntity player) {
        return KidnapperKnockoutService.shopEntry(player);
    }

    public static void addKnockoutDrugShopEntry(BuildShopEntries.ShopContext context, int index) {
        KidnapperKnockoutService.addKnockoutDrugShopEntry(context, index);
    }

    public static void addKnockoutDrugShopEntry(BuildShopEntries.ShopContext context, int index, PlayerEntity player) {
        KidnapperKnockoutService.addKnockoutDrugShopEntry(context, index, player);
    }

    public static boolean canUseKnockoutDrug(PlayerEntity player) {
        return KidnapperKnockoutService.canUseKnockoutDrug(player);
    }

    public static boolean isKnockoutDrug(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(SparkWitchItems.knockoutDrug());
    }
}
