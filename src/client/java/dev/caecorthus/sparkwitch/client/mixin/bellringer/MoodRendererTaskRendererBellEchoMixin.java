package dev.caecorthus.sparkwitch.client.mixin.bellringer;

import dev.caecorthus.sparkwitch.client.bellringer.BellEchoTaskLine;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restyles only the local owner's synced Echo task line in Wathe's top-left task list as red "must" text
 * with a countdown. Wathe-named members only, so {@code remap = false} needs no intermediary contract:
 * the {@code tick} selector carries no Minecraft type, and the {@code text} shadow's field type is
 * remapped with the class like other {@code remap = false} shadows (e.g. Wraith's mood mixin).
 * RETURN runs after Wathe rebuilt {@code text} for this frame, so fading rows keep their last line.
 * 仅将本地拥有者已同步的回响任务行在 Wathe 左上角任务列表中改为带倒计时的红色“必须”文本；
 * 只使用 Wathe 命名成员，因此 {@code remap = false} 无需 intermediary 选择器契约：{@code tick}
 * 选择器不含 Minecraft 类型，{@code text} 影子字段类型会像其他 {@code remap = false} 影子字段一样随类重映射。
 * RETURN 在 Wathe 本帧重建 {@code text} 之后执行，淡出中的行保留最后一次文本。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer", remap = false)
public abstract class MoodRendererTaskRendererBellEchoMixin {
    @Shadow
    public Text text;

    @Inject(
            method = "tick(Ldev/doctor4t/wathe/cca/PlayerMoodComponent$TrainTask;FZ)Z",
            at = @At("RETURN"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$restyleBellEchoTask(
            PlayerMoodComponent.TrainTask task,
            float delta,
            boolean fakeMood,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Text echoLine = BellEchoTaskLine.resolve(task);
        if (echoLine != null) {
            this.text = echoLine;
        }
    }
}
