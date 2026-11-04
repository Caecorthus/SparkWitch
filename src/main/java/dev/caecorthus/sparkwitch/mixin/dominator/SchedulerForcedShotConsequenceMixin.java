package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotScope;
import dev.doctor4t.wathe.util.Scheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * External seam on Wathe's public {@code Scheduler.schedule(Runnable, int)}: a task queued while a Dominator forced shot
 * fires runs inside that shot's consequence scope ({@link DominatorForcedShotScope#bindScheduled}), so a short-delayed
 * punishment or reward of the forced shooter (Wathe's 4-tick gun punishment, SparkFactionAPI's custom gun
 * punishment, SparkTraits' Scheduler-driven repeats) keeps the shot's protection. Tasks queued at any other time are
 * passed through unchanged.
 * Wathe 公共 {@code Scheduler.schedule(Runnable, int)} 上的外部接缝：支配者强制开枪期间排入的任务在该次开枪的后果作用域内运行，
 * 因此被强制开枪者的短延迟惩罚或奖励（Wathe 的 4 刻枪械惩罚、SparkFactionAPI 自定义枪罚、SparkTraits 经 Scheduler 的补射）
 * 保持这一枪的保护。其他时间排入的任务原样通过。
 */
@Mixin(value = Scheduler.class, remap = false)
public abstract class SchedulerForcedShotConsequenceMixin {
    @ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Runnable sparkwitch$bindForcedShotConsequence(Runnable action) {
        return DominatorForcedShotScope.bindScheduled(action);
    }
}
