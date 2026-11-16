package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.roles.civilian.planner.net.UsePlannerRecommendC2SPayload;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers 规划大师 outside the witch skill dispatcher: the {@code sparkwitch:use_planner_recommend} payload (common
 * initializer, so both sides know the type; only the server receiver acts, and Fabric already runs it on the server
 * thread), the replay formatters, the coins, reward and shop ({@link PlannerEconomyService}), the world tick
 * ({@link PlannerLifecycleService}) and the per-round state in {@link PlannerComponent}: role assignment drops the
 * player's recommendation and clears the component, then starts the 45 s opening cooldown for a drawn planner;
 * Wathe's {@code ResetPlayer} (every round end) clears it. The planner never gets a witch skill, so the witch skill
 * panel stays empty for them.
 * 在魔女技能分发路径之外注册规划大师：{@code sparkwitch:use_planner_recommend} 数据包（在通用初始化器中注册，两端都知道该
 * 类型；只有服务端接收器会执行，Fabric 已在服务端线程上运行它）、回放格式化器、金币、奖励与商店（{@link PlannerEconomyService}）、
 * 世界刻（{@link PlannerLifecycleService}），以及 {@link PlannerComponent} 中的每局状态：身份分配时移除该玩家的推荐并清空组件，
 * 再为抽到的规划大师开始 45 秒开局冷却；Wathe 的 {@code ResetPlayer}（每局结束）会清空它。规划大师从不获得魔女技能，因此魔女
 * 技能面板对其始终为空。
 */
public final class PlannerFeatureService {
    private static boolean registered;

    private PlannerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UsePlannerRecommendC2SPayload.ID, UsePlannerRecommendC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UsePlannerRecommendC2SPayload.ID,
                (payload, context) -> PlannerRecommendService.tryRecommend(context.player(), payload.target()));
        PlannerReplay.register();
        PlannerEconomyService.register();
        PlannerLifecycleService.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            PlannerLifecycleService.clear(serverPlayer);
            if (PlannerRules.isPlanner(role)) {
                PlannerComponent.KEY.get(serverPlayer).setSkillReadyTick(
                        serverPlayer.getWorld().getTime() + PlannerRules.INITIAL_COOLDOWN_TICKS);
            }
        });
        ResetPlayer.EVENT.register(PlannerLifecycleService::clear);
    }
}
