package dev.caecorthus.sparkwitch.client.mixin.natsume;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.natsume.NatsumeHudGarble;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 夏目安安 hypnosis HUD garble, draw side (rules and scope in {@link NatsumeHudGarble}). The two vanilla 1.21.1
 * {@code drawText} overloads every HUD renderer reaches swap their text argument at HEAD: the {@code Text} one (all
 * {@code drawTextWithShadow}/{@code drawCenteredTextWithShadow}/{@code drawTextWithBackground} Text calls, e.g. the
 * action bar, titles, scoreboard and Wathe's mood tasks) and the {@code String} one (the clock, money, cooldown digits
 * and F3). The Text overload only forwards to the {@code OrderedText} one, so nothing is garbled twice; direct
 * {@code OrderedText} draws are left alone. {@code @ModifyVariable}, not a cancelling re-draw, so other mods' hooks on
 * these methods still run once with the swapped value. {@code drawItemInSlot} suspends the scope so a hotbar stack
 * count stays an item decoration, like item names. Outside the HUD pass, with a screen open or when not hypnotised the
 * arguments pass through untouched. Client-only, display-only. Pinned in {@code NatsumeClientMixinTargetsTest}.
 * 夏目安安催眠 HUD 乱码的绘制端（规则与作用域见 {@link NatsumeHudGarble}）。原版 1.21.1 中所有 HUD 渲染都会走到的两个
 * {@code drawText} 重载在 HEAD 替换文字参数：{@code Text} 版（所有 {@code drawTextWithShadow}/{@code
 * drawCenteredTextWithShadow}/{@code drawTextWithBackground} 的 Text 调用，例如动作栏、标题、计分板与 Wathe 心情任务）与
 * {@code String} 版（时钟、金币、冷却数字与 F3）。Text 版只会转发到 {@code OrderedText} 版，因此不会重复乱码；直接绘制的
 * {@code OrderedText} 不处理。使用 {@code @ModifyVariable} 而非取消后重绘，其他模组在这些方法上的钩子仍只执行一次并看到替换
 * 后的值。{@code drawItemInSlot} 暂停作用域，使快捷栏堆叠数量与物品名一样作为物品装饰保留。不在 HUD 绘制中、打开界面或未被
 * 催眠时参数原样放行。仅客户端、仅展示。在 {@code NatsumeClientMixinTargetsTest} 中固定。
 */
@Mixin(DrawContext.class)
public abstract class NatsumeGarbleMixin {
    @ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text sparkwitch$garbleHudText(Text text) {
        return NatsumeHudGarble.garbleHud(text);
    }

    @ModifyVariable(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD"),
            argsOnly = true
    )
    private String sparkwitch$garbleHudString(String text) {
        return NatsumeHudGarble.garbleHud(text);
    }

    @WrapMethod(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
    private void sparkwitch$keepItemDecorations(TextRenderer textRenderer, ItemStack stack, int x, int y,
                                                String countOverride, Operation<Void> original) {
        NatsumeHudGarble.suspend();
        try {
            original.call(textRenderer, stack, x, y, countOverride);
        } finally {
            NatsumeHudGarble.resume();
        }
    }
}
