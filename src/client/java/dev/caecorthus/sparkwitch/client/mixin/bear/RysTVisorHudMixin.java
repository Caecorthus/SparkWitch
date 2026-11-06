package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.helmet.RysTVisorOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D6: the Rys-T visor overlay at {@code InGameHud.render} HEAD, before every HUD layer (so the hotbar, the Wathe HUD
 * and every SparkWitch overlay draw over it) and before the Holy Flash mask at TAIL. Default priority 1000 keeps it
 * after the ricochet double vision ({@code RicochetDazeHudMixin}, HEAD 900), so a dazed wearer sees the world doubled
 * under a crisp visor frame and cracks. {@code render} runs even under F1 (only its layers check {@code hudHidden}), so
 * F1 cannot clear the visor. All gates live in {@link RysTVisorOverlay#render}. Client presentation only.
 * D6：Rys-T 面罩遮罩位于 {@code InGameHud.render} HEAD，早于所有 HUD 层（因此快捷栏、Wathe HUD 与所有 SparkWitch 叠加层都画在
 * 其上），也早于 TAIL 处的圣光弹遮罩。默认优先级 1000 使其晚于跳弹重影（{@code RicochetDazeHudMixin}，HEAD 900），眩晕的佩戴者
 * 看到的是重影的世界与清晰的面罩边框和裂纹。F1 下 {@code render} 仍会执行（只有各层检查 {@code hudHidden}），所以 F1 无法去掉
 * 面罩。所有门槛都在 {@link RysTVisorOverlay#render} 中。仅客户端展示。
 */
@Mixin(InGameHud.class)
public abstract class RysTVisorHudMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD")
    )
    private void sparkwitch$renderRysTVisor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        RysTVisorOverlay.render(context);
    }
}
