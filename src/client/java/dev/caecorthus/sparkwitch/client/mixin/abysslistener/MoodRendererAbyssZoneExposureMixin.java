package dev.caecorthus.sparkwitch.client.mixin.abysslistener;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.abysslistener.AbyssZoneExposureTaskLine;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client presentation seam for the display-only pseudo task 「快离开这里！！！」 in Wathe's top-left task list
 * ({@link AbyssZoneExposureTaskLine}). Pinned Wathe 1.5.6 {@code renderHud}: the only {@code Map.isEmpty()} is the
 * {@code renderers.isEmpty()} mood-bar alpha gate, and {@code moodTextWidth} is written once per branch right before the
 * mood icon/bar are drawn (after the psycho early return and after every task row was drawn). Name-only selector and
 * JDK/Wathe-only targets, so {@code remap = false}; no {@code @Redirect}, so it coexists with the SparkTraits and Wraith
 * redirects in the same method. Never touches Wathe's task map.
 * Wathe 左上角任务列表中仅用于显示的临时任务「快离开这里！！！」的客户端表现接缝（{@link AbyssZoneExposureTaskLine}）。
 * 锁定 Wathe 1.5.6 {@code renderHud}：唯一的 {@code Map.isEmpty()} 是理智条透明度判断 {@code renderers.isEmpty()}，
 * {@code moodTextWidth} 在两个分支中各写入一次，紧接着绘制理智图标与理智条（位于疯魔提前返回与所有任务行绘制之后）。
 * 仅按名称选择方法且目标只含 JDK/Wathe 类型，因此 {@code remap = false}；不使用 {@code @Redirect}，可与同一方法中
 * SparkTraits 与冤魂的重定向共存。从不触碰 Wathe 任务表。
 */
@Mixin(value = MoodRenderer.class, remap = false)
public abstract class MoodRendererAbyssZoneExposureMixin {
    @Shadow
    @Final
    private static Map<PlayerMoodComponent.Task, ?> renderers;

    /** The pseudo line keeps the mood bar visible like a task row. / 临时任务行与任务行一样让理智条保持可见。 */
    @ModifyExpressionValue(
            method = "renderHud",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z", ordinal = 0),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$abyssLineKeepsMoodBar(boolean noTaskRows) {
        return noTaskRows && !AbyssZoneExposureTaskLine.occupiesRow(MinecraftClient.getInstance().player);
    }

    /** Draws the line below the last row and re-targets the mood bar at it. / 在最后一行下方绘制并让理智条以其为准。 */
    @Inject(
            method = "renderHud",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/MoodRenderer;moodTextWidth:F",
                    opcode = Opcodes.PUTSTATIC,
                    shift = At.Shift.AFTER
            ),
            require = 2,
            allow = 2
    )
    private static void sparkwitch$renderAbyssZoneExposureLine(
            PlayerEntity player,
            TextRenderer textRenderer,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        AbyssZoneExposureTaskLine.renderAfterLayout(
                player, textRenderer, context, tickCounter.getTickDelta(true), renderers.values());
    }
}
