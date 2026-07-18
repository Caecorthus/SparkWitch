package dev.caecorthus.sparkwitch.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Wraith-only access to Wathe's pinned task cadence internals.
 * 仅供冤魂访问 Wathe 固定的任务生成节奏内部状态。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public interface PlayerMoodComponentAccessor {
    @Accessor("nextTaskTimer")
    int sparkwitch$getNextTaskTimer();

    @Accessor("nextTaskTimer")
    void sparkwitch$setNextTaskTimer(int ticks);

    @Invoker("generateTask")
    PlayerMoodComponent.TrainTask sparkwitch$generateTask();
}
