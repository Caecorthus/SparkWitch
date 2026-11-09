package dev.caecorthus.sparkwitch.client.mixin.natsume;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.natsume.NatsumeHudGarble;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 夏目安安 hypnosis HUD garble, scope side (rules in {@link NatsumeHudGarble}). {@code render} opens the scope around the
 * whole vanilla 1.21.1 HUD pass, so every layer, Wathe's own HUD injects, SparkWitch's role HUD dispatcher and Fabric's
 * {@code HudRenderCallback} (all inside it) are covered; {@code renderChat} and {@code renderHeldItemTooltip} (the
 * held-item name popup) suspend it, so chat and item names are never garbled. Only {@code @WrapMethod}s that always call
 * the original, never a TAIL {@code @Inject}, so the role HUD seams keep their slot. Client-only, display-only. Pinned
 * in {@code NatsumeClientMixinTargetsTest}.
 * 夏目安安催眠 HUD 乱码的作用域端（规则见 {@link NatsumeHudGarble}）。{@code render} 在原版 1.21.1 整个 HUD 绘制外围开启
 * 作用域，因此所有图层、Wathe 自身的 HUD 注入、SparkWitch 职业 HUD 分发器与 Fabric 的 {@code HudRenderCallback}（都在其内）
 * 都被覆盖；{@code renderChat} 与 {@code renderHeldItemTooltip}（手持物品名弹出）暂停作用域，聊天与物品名永不乱码。只使用
 * 始终调用原方法的 {@code @WrapMethod}，不使用 TAIL {@code @Inject}，职业 HUD 接缝保持原位。仅客户端、仅展示。在
 * {@code NatsumeClientMixinTargetsTest} 中固定。
 */
@Mixin(InGameHud.class)
public abstract class NatsumeHudGarbleScopeMixin {
    @WrapMethod(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
    private void sparkwitch$scopeHudGarble(DrawContext context, RenderTickCounter tickCounter,
                                           Operation<Void> original) {
        NatsumeHudGarble.enterHud();
        try {
            original.call(context, tickCounter);
        } finally {
            NatsumeHudGarble.exitHud();
        }
    }

    @WrapMethod(method = "renderChat(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
    private void sparkwitch$keepChatReadable(DrawContext context, RenderTickCounter tickCounter,
                                             Operation<Void> original) {
        NatsumeHudGarble.suspend();
        try {
            original.call(context, tickCounter);
        } finally {
            NatsumeHudGarble.resume();
        }
    }

    @WrapMethod(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V")
    private void sparkwitch$keepHeldItemNameReadable(DrawContext context, Operation<Void> original) {
        NatsumeHudGarble.suspend();
        try {
            original.call(context);
        } finally {
            NatsumeHudGarble.resume();
        }
    }
}
