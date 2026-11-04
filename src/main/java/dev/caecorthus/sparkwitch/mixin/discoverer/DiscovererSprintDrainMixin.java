package dev.caecorthus.sparkwitch.mixin.discoverer;

import dev.caecorthus.sparkwitch.roles.civilian.discoverer.DiscovererRules;
import dev.caecorthus.sparkwitch.roles.civilian.discoverer.DiscovererStaminaService;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Discoverer sprint drain ×0.75 around Wathe's {@code wathe$limitSprint} (common config, vanilla target,
 * remap = true; the USEC/SparkTraits Excellent Physique capture pattern). Ordering is the contract: the capture at
 * {@code tickMovement} HEAD uses {@code order = 0}, before Wathe's default-order (1000) HEAD stamina tick; the rewrite
 * uses {@code order = 1500}, so it runs right after that tick and before the vanilla body (jump costs) and every TAIL
 * (Excellent Physique regen, Depression, the USEC regen boost). It touches sprinting ticks only, and those TAIL hooks
 * touch regeneration ticks only, so they never overlap. Runs on the server for every player and on a client for the
 * local player only; see {@link DiscovererStaminaService} for the authority split.
 * 探索者疾跑消耗 ×0.75，包在 Wathe 的 {@code wathe$limitSprint} 前后（通用配置，原版目标，remap = true；沿用 USEC 与
 * SparkTraits 体质优异的捕获写法）。顺序即契约：{@code tickMovement} HEAD 的捕获使用 {@code order = 0}，先于 Wathe 默认顺序
 * （1000）的 HEAD 体力刻；改写使用 {@code order = 1500}，因此紧接在该体力刻之后、原版方法体（跳跃消耗）与所有 TAIL
 * （体质优异恢复、抑郁、USEC 恢复加成）之前执行。它只处理疾跑刻，而这些 TAIL 只处理恢复刻，两者从不重叠。服务端对所有玩家
 * 执行，客户端只对本地玩家执行；权威划分见 {@link DiscovererStaminaService}。
 */
@Mixin(PlayerEntity.class)
public abstract class DiscovererSprintDrainMixin {
    @Unique
    private float sparkwitch$discovererPreviousSprintingTicks = DiscovererRules.NOT_CAPTURED;

    @Unique
    private int sparkwitch$discovererPreviousMaxSprintTime;

    @Inject(method = "tickMovement", at = @At("HEAD"), order = 0)
    private void sparkwitch$captureDiscovererStamina(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        sparkwitch$discovererPreviousSprintingTicks = DiscovererStaminaService.captureSprintingTicks(self);
        if (!Float.isNaN(sparkwitch$discovererPreviousSprintingTicks)) {
            sparkwitch$discovererPreviousMaxSprintTime = DiscovererStaminaService.captureMaxSprintTime(self);
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"), order = 1500)
    private void sparkwitch$slowDiscovererSprintDrain(CallbackInfo ci) {
        DiscovererStaminaService.slowSprintDrain((PlayerEntity) (Object) this,
                sparkwitch$discovererPreviousSprintingTicks, sparkwitch$discovererPreviousMaxSprintTime);
    }
}
