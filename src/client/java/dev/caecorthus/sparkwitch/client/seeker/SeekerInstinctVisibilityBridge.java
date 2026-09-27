package dev.caecorthus.sparkwitch.client.seeker;

import net.minecraft.entity.player.PlayerEntity;

/**
 * The Seeker's own copy of the SparkTraits isInstinctHidden(viewer, target) facade read (not Black Raven's bridge); absent or failing SparkTraits hides nothing.
 * TODO(WP-05): implement. / 待 WP-05 实现。
 * 搜寻者自有的一份 SparkTraits isInstinctHidden(viewer, target) 门面读取（不复用黑鸦的桥）；SparkTraits 缺失或失败时不隐藏任何人。
 */
public final class SeekerInstinctVisibilityBridge {
    private SeekerInstinctVisibilityBridge() {
    }

    public static boolean isHidden(PlayerEntity viewer, PlayerEntity target) {
        return false;
    }
}
