package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

/**
 * A wathe task wrapped so it reads as "规划大师推荐你去 …", expires after {@link PlannerRules#TASK_LIFETIME_TICKS}
 * and reports completion to the planner service instead of the normal task flow.
 * 包装 wathe 原生任务：文本显示为“规划大师推荐你去 ……”，超时自动消失，完成时交给规划大师服务结算。
 */
public final class PlannerRecommendTask implements PlayerMoodComponent.TrainTask {

    private final PlayerMoodComponent.Task type;
    private final PlayerMoodComponent.TrainTask delegate;
    private int remaining = PlannerRules.TASK_LIFETIME_TICKS;

    public PlannerRecommendTask(PlayerMoodComponent.Task type) {
        this.type = type;
        this.delegate = switch (type) {
            case SLEEP -> new PlayerMoodComponent.SleepTask(dev.doctor4t.wathe.game.GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new PlayerMoodComponent.OutsideTask(dev.doctor4t.wathe.game.GameConstants.OUTSIDE_TASK_DURATION);
            case EAT -> new PlayerMoodComponent.EatTask();
            case DRINK -> new PlayerMoodComponent.DrinkTask();
        };
    }

    @Override
    public void tick(@NotNull PlayerEntity player) {
        delegate.tick(player);
        remaining--;
        if (remaining == 0 && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            // Tell the client the marker is gone, so a later ordinary task of the same type is not mistaken for one.
            // 通知客户端标记已失效，避免之后同类型的普通任务被误认成推荐任务。
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                    new dev.caecorthus.sparkwitch.roles.civilian.planner.net.PlannerTaskAssignedS2CPayload(-1));
        }
    }

    @Override
    public boolean isFulfilled(PlayerEntity player) {
        if (remaining <= 0) {
            // Expired: drop it without paying out. 超时：直接移除，不结算奖励。
            return true;
        }
        return delegate.isFulfilled(player);
    }

    /** True when the removal was a timeout rather than a completion. 判断移除是因为超时还是完成。 */
    public boolean expired() {
        return remaining <= 0;
    }

    public PlayerMoodComponent.Task type() {
        return type;
    }

    @Override
    public String getName() {
        return "planner_recommend." + type.name().toLowerCase();
    }

    @Override
    public PlayerMoodComponent.Task getType() {
        return type;
    }

    @Override
    public NbtCompound toNbt() {
        return delegate.toNbt();
    }
}
