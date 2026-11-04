package dev.caecorthus.sparkwitch.client.mixin.noa;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.noa.NoaMoodIcon;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client presentation seam for Noa's cyan mood icon ({@link NoaMoodIcon}): while her displayed sanity is at or above
 * 0% the icon is a cyan copy of the same Wathe sprite; below 0% Wathe's original is drawn untouched (owner: "理智小于
 * 0% 调用 Wathe 原版的"). Pinned Wathe 1.5.6 {@code renderCivilian}: its only {@code drawGuiTexture(Identifier, int,
 * int, int, int)} call draws the icon Wathe picked from {@code moodRender}, so the same displayed value decides the
 * swap, in step with the bar's colour branch; the cyan sprite goes through the same operation chain at the same
 * footprint. {@code @WrapOperation}, never {@code @Redirect}, so it composes with any other wrap of that call and with
 * SparkTraits' {@code @ModifyArg}s on the bar's {@code MatrixStack.scale} and {@code MathHelper.hsvToRgb} in the same
 * method. Client-only and display-only: the server owns her sanity, and no value is ever shown.
 * 诺亚青色心情图标的客户端表现接缝（{@link NoaMoodIcon}）：其显示理智不低于 0% 时图标为同一张 Wathe sprite 的青色版；
 * 低于 0% 时原样绘制 Wathe 原版（所有者：“理智小于 0% 调用 Wathe 原版的”）。锁定 Wathe 1.5.6 {@code renderCivilian}：
 * 其中唯一的 {@code drawGuiTexture(Identifier, int, int, int, int)} 调用绘制 Wathe 依据 {@code moodRender} 选出的图标，
 * 因此由同一个显示值决定是否替换，与理智条的颜色分支保持一致；青色 sprite 以相同位置与尺寸走同一操作链。使用
 * {@code @WrapOperation} 而非 {@code @Redirect}，因此可与该调用的其他包装以及 SparkTraits 在同一方法中对理智条
 * {@code MatrixStack.scale} 与 {@code MathHelper.hsvToRgb} 的 {@code @ModifyArg} 共存。仅客户端、仅展示：她的理智由
 * 服务端负责，且从不显示任何数值。
 */
@Mixin(MoodRenderer.class)
public abstract class NoaMoodRendererMixin {
    @WrapOperation(
            method = "renderCivilian",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$noaCyanMoodIcon(
            DrawContext context,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> original
    ) {
        original.call(context, NoaMoodIcon.spriteFor(sprite), x, y, width, height);
    }
}
