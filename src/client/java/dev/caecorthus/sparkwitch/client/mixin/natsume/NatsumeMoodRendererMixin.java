package dev.caecorthus.sparkwitch.client.mixin.natsume;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.natsume.NatsumeMoodIcon;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client presentation seam for 夏目安安's sketchbook mood icon ({@link NatsumeMoodIcon}). Wathe 1.5.6
 * {@code renderHud} sends a REAL-mood role, her included (killer faction, {@code Role.MoodType.REAL}), to
 * {@code renderCivilian}; its only {@code drawGuiTexture(Identifier, int, int, int, int)} call draws the icon Wathe
 * picked from {@code moodRender}, so the sketchbook sprite goes through the same operation chain at the same 14x17
 * footprint and in step with the bar. Same pattern as Noa's {@code NoaMoodRendererMixin} on the same call, with its
 * own handler: both are {@code @WrapOperation}s (never {@code @Redirect}), each swaps only for its own role, so they
 * compose in either nesting order, as do SparkTraits' {@code @ModifyArg}s in that method. Client-only, display-only.
 * 夏目安安素描本心情图标的客户端表现接缝（{@link NatsumeMoodIcon}）。Wathe 1.5.6 {@code renderHud} 把真实理智职业（包括
 * 她：杀手阵营、{@code Role.MoodType.REAL}）交给 {@code renderCivilian}；其中唯一的
 * {@code drawGuiTexture(Identifier, int, int, int, int)} 调用绘制 Wathe 依据 {@code moodRender} 选出的图标，因此素描本
 * sprite 以相同的 14x17 尺寸走同一操作链，与理智条保持一致。与诺亚在同一调用上的 {@code NoaMoodRendererMixin} 同一写法、
 * 独立处理方法：两者都是 {@code @WrapOperation}（绝不用 {@code @Redirect}），各自只为本职业替换，任意嵌套顺序都能共存，
 * 与 SparkTraits 在该方法中的 {@code @ModifyArg} 同样兼容。仅客户端、仅展示。
 */
@Mixin(MoodRenderer.class)
public abstract class NatsumeMoodRendererMixin {
    @WrapOperation(
            method = "renderCivilian",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$natsumeSketchMoodIcon(
            DrawContext context,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> original
    ) {
        original.call(context, NatsumeMoodIcon.spriteFor(sprite), x, y, width, height);
    }
}
