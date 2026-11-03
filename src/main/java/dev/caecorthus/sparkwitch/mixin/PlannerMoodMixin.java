package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.component.PlannerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRecommendTask;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Wathe mood/task hooks for 规划大师.
 * <ul>
 *   <li>recommended tasks do not count toward the mood drain;</li>
 *   <li>the client re-wraps synced recommendations so they read as planner tasks;</li>
 *   <li>the server publishes the next-task countdown for the passive HUD line.</li>
 * </ul>
 * 规划大师的理智/任务钩子：推荐任务不计入理智流失；客户端把同步来的推荐任务重新包装以显示推荐文本；服务端发布下次
 * 任务倒计时供被动 HUD 使用。
 */
@Mixin(PlayerMoodComponent.class)
public abstract class PlannerMoodMixin {

    @Shadow
    @Final
    private PlayerEntity player;

    @Shadow
    private int nextTaskTimer;

    @Shadow
    @Final
    public Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> tasks;

    /**
     * Mood drain is {@code tasks.size() * MOOD_DRAIN}; recommendations must not add to it.
     * 理智流失是 {@code tasks.size() * MOOD_DRAIN}，推荐任务不应计入。
     */
    @Redirect(
            method = "serverTick",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I", ordinal = 0)
    )
    private int sparkwitch$plannerDrainCount(Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> map) {
        int recommendations = 0;
        for (PlayerMoodComponent.TrainTask task : map.values()) {
            if (task instanceof PlannerRecommendTask) {
                recommendations++;
            }
        }
        return map.size() - recommendations;
    }

    /** Publishes the countdown the passive HUD shows. 发布被动 HUD 使用的倒计时。 */
    @Inject(method = "serverTick", at = @At("TAIL"))
    private void sparkwitch$plannerPublishTimer(CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PlannerPlayerComponent.KEY.get(serverPlayer).setNextTaskTimer(nextTaskTimer);
        }
    }

    /**
     * The sync packet only carries task ordinals, so the client rebuilds plain tasks; re-wrap the recommended ones
     * here to restore the planner wording.
     * 同步包只带任务序号，客户端重建的是普通任务；此处把推荐任务重新包装，恢复推荐文本。
     */
    @Inject(method = "applySyncPacket", at = @At("TAIL"))
    private void sparkwitch$plannerRewrap(RegistryByteBuf buf, CallbackInfo ci) {
        PlannerPlayerComponent component = PlannerPlayerComponent.KEY.get(player);
        for (PlayerMoodComponent.Task type : PlayerMoodComponent.Task.values()) {
            if (component.isRecommended(type) && tasks.containsKey(type)) {
                tasks.put(type, new PlannerRecommendTask(type));
            }
        }
    }
}
