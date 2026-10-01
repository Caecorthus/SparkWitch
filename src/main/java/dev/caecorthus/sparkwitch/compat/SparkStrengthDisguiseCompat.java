package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * External seam: Black Raven disguise kits touch optional SparkStrength items by registry id only and fail closed.
 * No SparkStrength class is imported, loaded or reflected here; a missing mod or item simply grants nothing.
 * 外部接缝：黑羽鸦伪装物品只通过注册 id 接触可选的 SparkStrength 物品，缺失时失败关闭。
 * 此处不导入、不加载、不反射任何 SparkStrength 类；模组或物品缺失即不发放。
 */
public final class SparkStrengthDisguiseCompat {
    public static final String MOD_ID = "sparkstrength";
    /** SparkStrength's Attendant starter flashlight (SparkStrengthItems.FLASHLIGHT_ID). / SparkStrength 乘务员开局手电筒。 */
    public static final Identifier FLASHLIGHT_ID = Identifier.of(MOD_ID, "flashlight");

    private SparkStrengthDisguiseCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** A fresh flashlight stack, or EMPTY when SparkStrength or the item is absent. / 新手电筒物品；缺失时为 EMPTY。 */
    public static ItemStack flashlightStack() {
        return flashlightItem().map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * Mirrors SparkStrength's duplicate guard for the starter flashlight (main, armor and offhand slots).
     * 镜像 SparkStrength 开局手电筒的防重复检查（主背包、护甲与副手）。
     */
    public static boolean hasFlashlight(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        Optional<Item> flashlight = flashlightItem();
        if (flashlight.isEmpty()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(flashlight.get())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Item> flashlightItem() {
        if (!isLoaded()) {
            return Optional.empty();
        }
        try {
            return Registries.ITEM.getOrEmpty(FLASHLIGHT_ID);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
