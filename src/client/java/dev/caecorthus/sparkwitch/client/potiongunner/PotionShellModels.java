package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.renderer.ProjectileModels;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.util.Identifier;

/**
 * Client only: the standalone in-flight shell models. Every {@link PotionShellType} has a child of the shared geometry
 * {@code item/projectile/potion_shell} that only sets its texture ({@code potion_shell_<key>}), plus one type-agnostic
 * nozzle glow. No item points at them; the item and GUI icons stay the flat {@code *_shell} sprites.
 * 仅客户端：独立的飞行中炮弹模型。每种 {@link PotionShellType} 都有一个共用几何 {@code item/projectile/potion_shell}
 * 的子模型，只设置自己的贴图（{@code potion_shell_<key>}），另有一个与种类无关的喷口尾焰。没有物品引用它们；物品与
 * 界面图标仍是平面的 {@code *_shell} 贴图。
 */
public final class PotionShellModels {
    public static final Identifier FLARE_MODEL_ID = SparkWitch.id("item/projectile/potion_shell_flare");
    private static boolean registered;

    private PotionShellModels() {
    }

    /** Adds the four shell bodies and the flare to the model bakery once. / 只向模型烘焙加入一次四种弹体与尾焰。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PotionShellType[] types = PotionShellType.values();
        Identifier[] ids = new Identifier[types.length + 1];
        for (int i = 0; i < types.length; i++) {
            ids[i] = bodyModelId(types[i]);
        }
        ids[types.length] = FLARE_MODEL_ID;
        ProjectileModels.register(ids);
    }

    public static Identifier bodyModelId(PotionShellType type) {
        return SparkWitch.id("item/projectile/potion_shell_" + type.key());
    }
}
