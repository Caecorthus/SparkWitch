package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftGrayscaleFilter;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.SpectatorHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only HUD while inside a gate (gated first on {@link RiftSessionClient#isActive()}); the real inventory is never
 * touched (plan §6.4):
 * <ul>
 *   <li>{@code renderMainHud}: vanilla draws {@code SpectatorHud#renderSpectatorMenu} instead of the hotbar for a
 *   spectator; that call is wrapped to draw the ⬅️ ➡️ arrow bar, and {@code SpectatorHud#render} (the spectator command
 *   hint) is skipped.</li>
 *   <li>{@code renderHotbar}: {@code @WrapMethod} draws the arrow bar too, for frames where the inside flag arrived
 *   before the spectator game mode.</li>
 *   <li>{@code render} HEAD: the grey tint + vignette fallback, under every HUD layer, only when the post shader cannot
 *   run (Iris shader pack or load failure).</li>
 * </ul>
 * Priority 1100: each {@code @WrapMethod} wraps whatever body exists when it is applied (ascending priority), so the
 * higher-priority wrapper is outermost — the {@code SeekerRemoteInGameHudMixin} reasoning; Wathe only re-textures the
 * hotbar inside it at the default 1000.
 * 门内的纯客户端 HUD（第一步判断 {@link RiftSessionClient#isActive()}）；真实背包从不改动（plan §6.4）：{@code renderMainHud} 中原版对
 * 旁观者绘制 {@code SpectatorHud#renderSpectatorMenu} 而非快捷栏，此调用被包装为绘制 ⬅️ ➡️ 箭头栏，并跳过
 * {@code SpectatorHud#render}（旁观者命令提示）；{@code renderHotbar} 用 {@code @WrapMethod} 同样绘制箭头栏，覆盖「门内标志先于旁观者
 * 游戏模式到达」的帧；{@code render} HEAD 在所有 HUD 层之下绘制灰色叠层与暗角回退，仅在后处理着色器无法运行时（Iris 光影包或加载
 * 失败）。优先级 1100：每个 {@code @WrapMethod} 包装其被应用时已存在的方法体（按优先级升序），因此优先级更高者位于最外层——与
 * {@code SeekerRemoteInGameHudMixin} 的理由相同；Wathe 只在其内部以默认 1000 替换快捷栏贴图。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class RiftSessionInGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sparkwitch$renderRiftFallback(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        if (RiftGrayscaleFilter.fallbackActive()) {
            RiftSessionHud.renderFallback(context);
        }
    }

    @WrapMethod(method = "renderHotbar")
    private void sparkwitch$riftArrowsInsteadOfHotbar(DrawContext context, RenderTickCounter tickCounter,
                                                      Operation<Void> original) {
        if (!RiftSessionClient.isActive()) {
            original.call(context, tickCounter);
            return;
        }
        RiftSessionHud.renderBar(context);
    }

    @WrapOperation(method = "renderMainHud", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/SpectatorHud;renderSpectatorMenu(Lnet/minecraft/client/gui/DrawContext;)V"))
    private void sparkwitch$riftArrowsInsteadOfSpectatorMenu(SpectatorHud spectatorHud, DrawContext context,
                                                             Operation<Void> original) {
        if (!RiftSessionClient.isActive()) {
            original.call(spectatorHud, context);
            return;
        }
        RiftSessionHud.renderBar(context);
    }

    @WrapOperation(method = "renderMainHud", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/SpectatorHud;render(Lnet/minecraft/client/gui/DrawContext;)V"))
    private void sparkwitch$hideSpectatorHintInsideRift(SpectatorHud spectatorHud, DrawContext context,
                                                       Operation<Void> original) {
        if (!RiftSessionClient.isActive()) {
            original.call(spectatorHud, context);
        }
    }
}
