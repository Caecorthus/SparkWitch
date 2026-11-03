package dev.caecorthus.sparkwitch.client.mixin.planner;

import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRecommendTask;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recolours the mood task line when the task is a 规划大师 recommendation.
 * <p>Wathe draws every task row with one shared white colour, so the tint has to live in the text style itself.
 * 当任务行是规划大师的推荐任务时改用身份色。Wathe 用统一的白色绘制所有任务行，因此颜色必须写在文本样式里。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer")
public abstract class PlannerTaskColorMixin {

    @Shadow
    public Text text;

    @Inject(method = "tick", at = @At("TAIL"))
    private void sparkwitch$plannerTaskColor(PlayerMoodComponent.TrainTask present, float delta,
                                             boolean isFakeMood, CallbackInfoReturnable<Boolean> cir) {
        // Judge by the synced marker, not by the runtime class: the client rebuilds ordinary dummy tasks after every
        // sync, so the row would otherwise lose its recommendation styling.
        // 按同步来的标记判断，而不是看运行时类型：客户端每次同步后都会重建普通占位任务，
        // 否则这一行会丢掉推荐样式。
        // Present can be null while Wathe rebuilds the row; calling getType() on it crashes the whole HUD pass.
        // Wathe 重建这一行时 present 可能为 null；对它调用 getType() 会让整个 HUD 渲染崩溃。
        if (present == null) {
            return;
        }
        net.minecraft.entity.player.PlayerEntity owner = net.minecraft.client.MinecraftClient.getInstance().player;
        if (owner == null || !dev.caecorthus.sparkwitch.component.PlannerPlayerComponent.KEY
                .get(owner).isRecommended(present.getType())) {
            return;
        }
        // The recommendation key already reads as a whole sentence ("规划大师推荐你去 吃点东西"), so the row must be
        // that key alone: adding Wathe's "task.feel" prefix would glue two sentences together and leave the prefix white.
        // 推荐键本身已是完整句子（“规划大师推荐你去 吃点东西”），因此整行只放这个键：
        // 再套 Wathe 的 task.feel 前缀会把两句拼在一起，而且前缀还是白色。
        MutableText line = Text.translatable("task." + present.getName());
        line.setStyle(line.getStyle().withColor(PlannerRules.COLOR));
        line.formatted(Formatting.ITALIC);
        text = line;
    }
}
