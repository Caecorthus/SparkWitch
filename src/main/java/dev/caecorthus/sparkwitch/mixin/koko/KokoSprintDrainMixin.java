package dev.caecorthus.sparkwitch.mixin.koko;

import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoStaminaService;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Koko's sprint drain ×0.5 while she is live, around Wathe's {@code wathe$limitSprint} (common config, vanilla target,
 * remap = true; the Discoverer capture pattern). Ordering is the contract: the capture at {@code tickMovement} HEAD
 * uses {@code order = 0}, before Wathe's default-order (1000) HEAD stamina tick; the rewrite uses {@code order = 1500},
 * so it runs right after that tick and before the vanilla body (jump costs) and every TAIL (regeneration hooks). It
 * touches sprinting ticks only. A player has one role, so this rewrite and the Discoverer's never act on the same
 * player. Runs on the server for every player and on a client for the local player only; see
 * {@link KokoStaminaService} for the authority split.
 * 可可直播期间疾跑消耗 ×0.5，包在 Wathe 的 {@code wathe$limitSprint} 前后（通用配置，原版目标，remap = true；沿用探索者的
 * 捕获写法）。顺序即契约：{@code tickMovement} HEAD 的捕获使用 {@code order = 0}，先于 Wathe 默认顺序（1000）的 HEAD 体力刻；
 * 改写使用 {@code order = 1500}，因此紧接在该体力刻之后、原版方法体（跳跃消耗）与所有 TAIL（恢复类钩子）之前执行。它只处理
 * 疾跑刻。一名玩家只有一个职业，因此本改写与探索者的改写从不作用于同一名玩家。服务端对所有玩家执行，客户端只对本地玩家执行；
 * 权威划分见 {@link KokoStaminaService}。
 */
@Mixin(PlayerEntity.class)
public abstract class KokoSprintDrainMixin {
    @Unique
    private float sparkwitch$kokoPreviousSprintingTicks = KokoRules.NOT_CAPTURED;

    @Unique
    private int sparkwitch$kokoPreviousMaxSprintTime;

    @Inject(method = "tickMovement", at = @At("HEAD"), order = 0)
    private void sparkwitch$captureKokoStamina(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        sparkwitch$kokoPreviousSprintingTicks = KokoStaminaService.captureSprintingTicks(self);
        if (!Float.isNaN(sparkwitch$kokoPreviousSprintingTicks)) {
            sparkwitch$kokoPreviousMaxSprintTime = KokoStaminaService.captureMaxSprintTime(self);
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"), order = 1500)
    private void sparkwitch$halveKokoSprintDrain(CallbackInfo ci) {
        KokoStaminaService.halveSprintDrain((PlayerEntity) (Object) this,
                sparkwitch$kokoPreviousSprintingTicks, sparkwitch$kokoPreviousMaxSprintTime);
    }
}
