package dev.caecorthus.sparkwitch.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to Wathe's pinned task cadence internals: the Wraith task runtime reads and writes them; the 规划大师 Master
 * Planner only reads {@code nextTaskTimer} (server, its own next-task HUD line) and never writes it.
 * 访问 Wathe 固定的任务生成节奏内部状态：冤魂任务运行时读写它们；规划大师只读取 {@code nextTaskTimer}（服务端，用于自身的
 * 下一任务 HUD 行），从不写入。
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
