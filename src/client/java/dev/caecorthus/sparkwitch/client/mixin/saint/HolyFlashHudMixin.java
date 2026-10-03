package dev.caecorthus.sparkwitch.client.mixin.saint;

import dev.caecorthus.sparkwitch.client.saint.HolyFlashOverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only Holy Flash mask at {@code InGameHud.render} TAIL. Priority 1100: TAIL injectors run in ascending
 * mixin priority, so this lands after Fabric API's {@code HudRenderCallback} (Time Stealer row, Seeker CCTV frame,
 * every SparkWitch overlay) and after the Kidnapper control screen, all at the default 1000. {@code render} runs even
 * under F1 (only its layers check {@code hudHidden}), so F1 cannot peel the mask off. All gates live in
 * {@link HolyFlashOverlayRenderer#render}.
 * 纯客户端圣光弹遮罩，注入 {@code InGameHud.render} TAIL。优先级 1100：TAIL 注入按 mixin 优先级升序执行，
 * 因此位于 Fabric API 的 {@code HudRenderCallback}（窃时者行、搜寻者 CCTV 边框及所有 SparkWitch 叠加层）以及绑匪
 * 控制黑屏（均为默认 1000）之后。F1 下 {@code render} 仍会执行（只有各层检查 {@code hudHidden}），所以 F1
 * 无法去掉遮罩。所有门槛都在 {@link HolyFlashOverlayRenderer#render} 中。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class HolyFlashHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderHolyFlash(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HolyFlashOverlayRenderer.render(context, tickCounter);
    }
}
