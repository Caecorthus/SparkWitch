package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32Hud;
import dev.doctor4t.wathe.client.gui.CooldownRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the M32A1 slot badge shows ({@link M32Hud#isShown}), Wathe's own cooldown number above the selected slot (its
 * one {@code drawTextWithShadow} in {@code CooldownRenderer#renderHud}, centred on the slot at {@code h - 35}) is
 * skipped: it would sit under the badge's count, and the badge already shows the launcher's cooldown (dimmed rounds and
 * the bar). Only that draw is wrapped, never the whole method, so the Grand Witch sword's HEAD injector on the same
 * method runs unchanged in any order; any other held item, or the launcher while the badge is hidden, keeps Wathe's
 * number. Pinned in {@code verifyClientMixinSelectors}.
 * M32A1 格标记显示期间（{@link M32Hud#isShown}），跳过 Wathe 自己在选中格上方的冷却数字（{@code CooldownRenderer#renderHud}
 * 中唯一的 {@code drawTextWithShadow}，以该格为中心、位于 {@code h - 35}）：它会落在标记的弹数下面，而标记已显示发射器的
 * 冷却（变暗的榴弹与进度条）。只包装这一次绘制而非整个方法，因此大魔女仪礼剑在同一方法上的 HEAD 注入无论先后都不受影响；
 * 手持其他物品，或标记未显示时手持发射器，都保留 Wathe 数字。已在 {@code verifyClientMixinSelectors} 中固定。
 */
@Mixin(CooldownRenderer.class)
public abstract class M32CooldownNumberMixin {
    @WrapOperation(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"
            )
    )
    private static int sparkwitch$yieldToM32Badge(
            DrawContext context,
            TextRenderer renderer,
            String text,
            int x,
            int y,
            int color,
            Operation<Integer> original
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (M32Hud.isShown(client, client.player)) {
            return 0;
        }
        return original.call(context, renderer, text, x, y, color);
    }
}
