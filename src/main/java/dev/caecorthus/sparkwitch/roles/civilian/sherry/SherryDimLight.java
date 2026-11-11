package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime half of 橘雪莉's dim-light reprieve, called from {@code PlayerMoodComponentSherryDimLightMixin} on both
 * sides for every player with pending tasks: the cheap role test comes first, the light read only for a Sherry. The
 * light level is the world's combined level at her feet (block light, or sky light less the ambient darkness).
 * 橘雪莉暗处理智补偿的运行时部分，由 {@code PlayerMoodComponentSherryDimLightMixin} 在双端对每个有待办任务的玩家调用：
 * 先做廉价的职业判断，只有雪莉才读取亮度。亮度取她脚下的世界综合亮度（方块光，或扣除环境暗度后的天空光）。
 */
public final class SherryDimLight {
    private SherryDimLight() {
    }

    public static float scaleDrain(@Nullable PlayerEntity player, float drain) {
        if (player == null || player.getWorld() == null) {
            return drain;
        }
        boolean sherry = SherryRules.isSherry(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
        if (!sherry) {
            return drain;
        }
        return SherryRules.scaleMoodDrain(drain, true, player.getWorld().getLightLevel(player.getBlockPos()));
    }
}
