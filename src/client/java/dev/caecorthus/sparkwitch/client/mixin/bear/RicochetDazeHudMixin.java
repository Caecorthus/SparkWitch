package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.ricochet.RysTDazeGhostRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only Rys-T double vision (D5) at {@code InGameHud.render} HEAD: the frame then holds the world and the hand
 * but no HUD layer, so the ghost doubles the view, never the hotbar or text. Priority 1100: HEAD injectors run in
 * ascending mixin priority (all HEAD points resolve before any is injected), so this runs after default-priority HEAD
 * overlays such as the BEAR visor and doubles them too. {@code render} also runs under F1. Never cancels; all gates
 * live in {@link RysTDazeGhostRenderer#render}.
 * 纯客户端的 Rys-T 重影（D5），注入 {@code InGameHud.render} HEAD：此时画面已有世界与手部、尚无任何 HUD 层，因此重影只叠加
 * 视野，不叠加快捷栏或文字。优先级 1100：HEAD 注入按 mixin 优先级升序执行（所有 HEAD 注入点在注入前统一解析），因此位于默认
 * 优先级的 HEAD 叠加层（如 BEAR 面罩）之后，并把它们一起叠成重影。F1 下 {@code render} 仍会执行。从不取消；所有门槛都在
 * {@link RysTDazeGhostRenderer#render} 中。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class RicochetDazeHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sparkwitch$renderRysTDazeGhost(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        RysTDazeGhostRenderer.render(context, tickCounter);
    }
}
