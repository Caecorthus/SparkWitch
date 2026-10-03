package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop.AccompliceShopRules;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Potion Gunner shop: the plain Accomplice list without the revolver, then the four shells. Shell display stacks are
 * our own items, never {@code wathe:grenade}, so SparkStrength never appends its M67 here; the SparkStrength tablet is
 * still appended for the witch faction.
 * 药炮手商店：普通共犯条目去掉左轮，再追加四种炮弹。炮弹展示物品是本模组物品，从不是 {@code wathe:grenade}，因此
 * SparkStrength 不会在这里追加 M67；SparkStrength 平板仍按魔女阵营追加。
 */
public final class PotionGunnerShopRules {
    /** Accomplice entry the Potion Gunner never sells. / 药炮手不出售的共犯条目。 */
    public static final String REMOVED_ACCOMPLICE_ENTRY = "revolver";

    private PotionGunnerShopRules() {
    }

    public static List<ShopEntry> entries() {
        List<ShopEntry> entries = new ArrayList<>(AccompliceShopRules.entriesWithout(REMOVED_ACCOMPLICE_ENTRY));
        for (PotionShellType type : PotionShellType.values()) {
            entries.add(new ShopEntry.Builder(
                    type.shopEntryId(),
                    new ItemStack(SparkWitchItems.potionShell(type)),
                    type.price(),
                    ShopEntry.Type.WEAPON
            ).onBuy(player -> giveShell(player, type)).build());
        }
        return List.copyOf(entries);
    }

    /**
     * Shells stack up to {@link PotionGunnerRules#SHELL_MAX_STACK}: a purchase tops up an existing same-type hotbar
     * stack first, then takes an empty hotbar slot. Loading needs both the shell and the launcher in the hotbar,
     * because Wathe's inventory screen only exposes hotbar slots.
     * 炮弹最多叠 {@link PotionGunnerRules#SHELL_MAX_STACK} 发：购买先补到快捷栏里同类未满的一堆，再占一个空快捷栏格。
     * 装填要求炮弹和炮筒都在快捷栏，因为 Wathe 背包界面只显示快捷栏。
     */
    public static boolean giveShell(PlayerEntity player, PotionShellType type) {
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(SparkWitchItems.potionShell(type)) && stack.getCount() < stack.getMaxCount()) {
                stack.increment(1);
                return true;
            }
        }
        for (int slot = 0; slot < hotbar; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                inventory.setStack(slot, new ItemStack(SparkWitchItems.potionShell(type)));
                return true;
            }
        }
        return false;
    }
}
