package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowHud;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only HUD while flying the crow: {@code renderHotbar} HEAD draws the one-slot crow hotbar (Homing Feather)
 * instead of the real hotbar, which is never touched; {@code renderCrosshair} is wrapped to draw a plain crosshair
 * instead of Wathe's {@code CrosshairRenderer}, whose knife/gun hints come from the body. Priority 1100 keeps the
 * wrapper outside Wathe's own priority-1000 {@code renderCrosshair} wrapper, which never calls the original in a game
 * (same reasoning as {@code SeekerRemoteInGameHudMixin}). First statement: the {@code isActive()} gate.
 * 操控乌鸦期间的纯客户端 HUD：renderHotbar 开头绘制只有一格（归羽）的乌鸦快捷栏代替真实快捷栏（真实快捷栏从不改动）；renderCrosshair
 * 被包装为绘制普通准星，代替基于本体刀/枪提示的 Wathe CrosshairRenderer。优先级 1100 使包装器位于 Wathe 自身优先级 1000、对局中从不调用
 * 原方法的 renderCrosshair 包装器之外（理由同 SeekerRemoteInGameHudMixin）。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class BlackRavenCrowInGameHudMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$renderCrowHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowHud.renderHotbar(context);
        ci.cancel();
    }

    @WrapMethod(method = "renderCrosshair")
    private void sparkwitch$renderCrowCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                               Operation<Void> original) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            original.call(context, tickCounter);
            return;
        }
        BlackRavenCrowHud.renderCrosshair(context);
    }
}
