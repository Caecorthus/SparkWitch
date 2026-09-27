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
 * External seam: the Seeker touches the optional SparkStrength tablet by registry id and entry-id string only. No
 * SparkStrength class is imported, loaded or reflected here; a missing mod or item simply means "no tablet".
 * 外部接缝：搜寻者只通过注册 id 与条目 id 字符串接触可选的 SparkStrength 平板。
 * 此处不导入、不加载、不反射任何 SparkStrength 类；模组或物品缺失即视为“没有平板”。
 */
public final class SparkStrengthTabletCompat {
    public static final String MOD_ID = "sparkstrength";
    public static final Identifier TABLET_ID = Identifier.of(MOD_ID, "tablet");
    /** SparkStrength's tablet shop-entry id; pinned by a source test. / SparkStrength 平板商店条目 id，由源码测试固定。 */
    public static final String SS_TABLET_ENTRY_ID = "sparkstrength_tablet";

    private static volatile Item cachedTablet;

    private SparkStrengthTabletCompat() {
    }

    /**
     * The tablet item, resolved lazily after registration and cached once found.
     * 平板物品；注册完成后惰性解析，找到后缓存。
     */
    public static Optional<Item> tabletItem() {
        Item tablet = cachedTablet;
        if (tablet != null) {
            return Optional.of(tablet);
        }
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return Optional.empty();
        }
        Optional<Item> resolved = Registries.ITEM.getOrEmpty(TABLET_ID);
        resolved.ifPresent(item -> cachedTablet = item);
        return resolved;
    }

    public static boolean isAvailable() {
        return tabletItem().isPresent();
    }

    public static boolean isTablet(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && TABLET_ID.equals(Registries.ITEM.getId(stack.getItem()));
    }

    /**
     * The spec's "current inventory": main inventory, hotbar, offhand and the cursor stack. Deliberately not
     * SparkStrength's hotbar-only access rule.
     * 规格中的“当前背包”：主背包、快捷栏、副手与光标上的物品。刻意不采用 SparkStrength 的仅快捷栏规则。
     */
    public static boolean hasTabletAnywhere(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.main) {
            if (isTablet(stack)) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offHand) {
            if (isTablet(stack)) {
                return true;
            }
        }
        return player.currentScreenHandler != null && isTablet(player.currentScreenHandler.getCursorStack());
    }
}
