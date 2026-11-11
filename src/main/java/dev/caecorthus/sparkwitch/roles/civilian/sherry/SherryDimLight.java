package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime half of 橘雪莉's dim-light reprieve, called from {@code PlayerMoodComponentSherryDimLightMixin} on both
 * sides for every player with pending tasks: the cheap role test comes first, the light read only for a Sherry. The
 * light level is the combined level at her feet (block light, or sky light less the ambient darkness). The darkness
 * is recomputed here from the synced time and weather with vanilla's own formula, because {@code ClientWorld} only
 * computes its stored value once while {@code ServerWorld} refreshes it every tick: reading the stored value would
 * make the client predict full drain at night or in rain where the server halves it.
 * 橘雪莉暗处理智补偿的运行时部分，由 {@code PlayerMoodComponentSherryDimLightMixin} 在双端对每个有待办任务的玩家调用：
 * 先做廉价的职业判断，只有雪莉才读取亮度。亮度取她脚下的综合亮度（方块光，或扣除环境暗度后的天空光）。环境暗度在此处
 * 用原版公式根据已同步的时间与天气重新计算：{@code ClientWorld} 只在创建时计算一次其保存值，而 {@code ServerWorld}
 * 每 tick 刷新；直接读取保存值会让客户端在夜晚或雨天预测为全额消耗，而服务端实际减半。
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
        World world = player.getWorld();
        return SherryRules.scaleMoodDrain(drain, true,
                world.getLightLevel(player.getBlockPos(), ambientDarkness(world)));
    }

    /**
     * Vanilla {@code World.calculateAmbientDarkness}, from values both sides hold (rain and thunder gradients, sky
     * angle). / 原版 {@code World.calculateAmbientDarkness} 的公式，输入均为双端都持有的数值（雨、雷暴渐变与天空角度）。
     */
    static int ambientDarkness(World world) {
        double rain = 1.0D - (double) (world.getRainGradient(1.0F) * 5.0F) / 16.0D;
        double thunder = 1.0D - (double) (world.getThunderGradient(1.0F) * 5.0F) / 16.0D;
        double sun = 0.5D + 2.0D * MathHelper.clamp(
                (double) MathHelper.cos(world.getSkyAngle(1.0F) * (float) (Math.PI * 2.0D)), -0.25D, 0.25D);
        return (int) ((1.0D - sun * rain * thunder) * 11.0D);
    }
}
