package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.item.Item;

/**
 * One Potion Gunner shell type. Shells are loaded into the launcher from the inventory cursor; they do nothing when
 * used on their own.
 * 一种药炮手炮弹。炮弹通过背包光标装入炮筒；单独使用没有效果。
 */
public class PotionShellItem extends Item {
    private final PotionShellType type;

    public PotionShellItem(PotionShellType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public static Settings createSettings() {
        return new Settings().maxCount(PotionGunnerRules.SHELL_MAX_STACK);
    }

    public PotionShellType shellType() {
        return type;
    }
}
