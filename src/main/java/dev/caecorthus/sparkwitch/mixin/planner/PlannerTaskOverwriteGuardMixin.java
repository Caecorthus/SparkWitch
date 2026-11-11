package dev.caecorthus.sparkwitch.mixin.planner;

import dev.caecorthus.sparkwitch.component.PlannerPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Keeps a recommended task from being overwritten by the routine task refresh.
 * <p>Wathe refills {@code tasks} with {@code tasks.put(task.getType(), task)} once the timer fires. When that type is
 * currently a 规划大师 recommendation, the put is dropped: the recommendation owns that slot until it is finished or
 * expires, so a freshly generated ordinary task can never silently replace it.
 * 防止推荐任务被例行刷新覆盖。Wathe 在计时器到点时会填入新任务；若该类型当前是规划大师的推荐任务，
 * 则丢弃这次写入——该槽位归推荐任务所有，直到完成或超时。
 */
@Mixin(PlayerMoodComponent.class)
public abstract class PlannerTaskOverwriteGuardMixin {

    @Shadow
    @Final
    private PlayerEntity player;

    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object sparkwitch$skipRecommendedOverwrite(Map map, Object key, Object value) {
        if (key instanceof PlayerMoodComponent.Task type
                && PlannerPlayerComponent.KEY.get(player).isRecommended(type)) {
            return null;
        }
        return map.put(key, value);
    }
}
