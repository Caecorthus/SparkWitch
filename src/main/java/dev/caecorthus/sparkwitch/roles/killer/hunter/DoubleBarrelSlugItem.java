package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * Slug: one solid projectile on the centre ray at {@link HunterRules#SLUG_RANGE} (2026-10-29).
 * 独头弹：单颗实心弹丸，沿中心射线，射程 {@link HunterRules#SLUG_RANGE}（2026-10-29）。
 */
public final class DoubleBarrelSlugItem extends DoubleBarrelAmmoItem {
    public static final Identifier ID = SparkWitch.id("double_barrel_slug");

    public DoubleBarrelSlugItem(Settings settings) {
        super(settings);
    }

    @Override
    public int shellType() {
        return HunterRules.SHELL_SLUG;
    }
}
