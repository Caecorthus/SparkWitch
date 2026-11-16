package dev.caecorthus.sparkwitch.client.mixin.planner;

import dev.caecorthus.sparkwitch.client.planner.PlannerTaskLine;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client presentation seam for the Master Planner's recommendation: the local player's recommended task row in
 * Wathe's top-left list reads {@code task.planner_recommend.<type>} in the planner colour and italic
 * ({@link PlannerTaskLine}). Pinned Wathe 1.5.6 {@code MoodRenderer$TaskRenderer.tick(TrainTask, float, boolean)}
 * rebuilds {@code text} from {@code present} every frame, so TAIL (its single return) runs after that rebuild and the
 * replacement never sticks: a cleared marker shows Wathe's own line on the next frame, and a fading row ({@code present}
 * null) keeps its last line. Wathe-named members only, so {@code remap = false} like the Bell Echo row mixin on the same
 * method; the {@code text} shadow's type is remapped with the class. It only assigns the field, never cancels, so it
 * composes with that mixin in any order (the Echo row wins by rule, not by order). Pinned in
 * {@code watheClientMixinContracts}. Display only: the server owns the task and the marker.
 * 规划大师推荐的客户端展示接缝：本地玩家在 Wathe 左上角列表中被推荐的任务行显示为规划大师颜色、斜体的
 * {@code task.planner_recommend.<type>}（{@link PlannerTaskLine}）。锁定 Wathe 1.5.6
 * {@code MoodRenderer$TaskRenderer.tick(TrainTask, float, boolean)}：它每帧依据 {@code present} 重建 {@code text}，因此 TAIL
 * （唯一的返回点）在重建之后执行，替换不会残留：标记清除后下一帧即显示 Wathe 原文本，淡出中的行（{@code present} 为 null）
 * 保留最后一次文本。只使用 Wathe 命名成员，因此与同一方法上的回响任务行 mixin 一样使用 {@code remap = false}；
 * {@code text} 影子字段类型随类重映射。只赋值字段、从不取消，因此与该 mixin 以任意顺序共存（回响行按规则而非顺序优先）。
 * 固定在 {@code watheClientMixinContracts} 中。仅为展示：任务与标记由服务端负责。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer", remap = false)
public abstract class PlannerTaskRowMixin {
    @Shadow
    public Text text;

    @Inject(
            method = "tick(Ldev/doctor4t/wathe/cca/PlayerMoodComponent$TrainTask;FZ)Z",
            at = @At("TAIL"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$plannerRecommendedTaskRow(
            PlayerMoodComponent.TrainTask present,
            float delta,
            boolean fakeMood,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Text line = PlannerTaskLine.resolve(present);
        if (line != null) {
            this.text = line;
        }
    }
}
