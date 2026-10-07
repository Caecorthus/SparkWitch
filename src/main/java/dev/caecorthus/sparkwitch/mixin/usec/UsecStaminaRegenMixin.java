package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecStaminaService;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * USEC regen ×2, after the SparkTraits Excellent Physique pattern (common config, vanilla target, remap = true).
 * Ordering is the contract: the HEAD capture uses {@code order = 0}, so it runs before Wathe's default-order
 * {@code wathe$limitSprint} HEAD regen; the TAIL boost runs at default order in a priority-1500 mixin, so it is
 * applied after every priority-1000 TAIL, including SparkTraits' Excellent Physique bonus, which it therefore also
 * doubles (a deliberate multiplicative stack). Runs on the server for every player and on a client for the local player
 * only; see {@link UsecStaminaService} for the authority split.
 * USEC 体力恢复 ×2，沿用 SparkTraits 体质优异的写法（通用配置，原版目标，remap = true）。顺序即契约：HEAD 捕获使用
 * {@code order = 0}，因此先于 Wathe 默认顺序的 {@code wathe$limitSprint} HEAD 恢复执行；TAIL 加成在优先级 1500 的
 * mixin 中以默认顺序执行，因此排在所有优先级 1000 的 TAIL 之后，包括 SparkTraits 体质优异加成，并会一并翻倍（有意的乘法叠加）。
 * 服务端对所有玩家执行，客户端只对本地玩家执行；权威划分见 {@link UsecStaminaService}。
 */
@Mixin(value = PlayerEntity.class, priority = 1500)
public abstract class UsecStaminaRegenMixin {
    @Unique
    private float sparkwitch$usecPreviousSprintingTicks;

    @Inject(method = "tickMovement", at = @At("HEAD"), order = 0)
    private void sparkwitch$captureUsecStamina(CallbackInfo ci) {
        sparkwitch$usecPreviousSprintingTicks = UsecStaminaService.capture((PlayerEntity) (Object) this);
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void sparkwitch$doubleUsecStaminaRegen(CallbackInfo ci) {
        UsecStaminaService.boostRegen((PlayerEntity) (Object) this, sparkwitch$usecPreviousSprintingTicks);
    }
}
