package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import net.minecraft.item.Item;

/**
 * The Potion Gunner's bound anti-tank launcher. Holding use aims down the scope; loading happens in the inventory.
 * Firing is a separate C2S intent handled by {@link PotionLauncherFireService}. It is deliberately outside
 * {@code wathe:guns}, so Wathe gun punishment, gun pickup, and gun packets never apply.
 * 药炮手绑定的反坦克炮筒。按住使用键开镜；装填在背包中完成。发射是独立的 C2S 意图，由
 * {@link PotionLauncherFireService} 处理。刻意不加入 {@code wathe:guns}，因此 Wathe 的误杀惩罚、拾枪与开枪数据包都不适用。
 */
public class PotionLauncherItem extends Item {
    public PotionLauncherItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }
}
