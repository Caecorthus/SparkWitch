package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindCrosshair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * C6 crosshair gate on the vanilla path: Wathe's {@code InGameHud.renderCrosshair} wrap hands the crosshair to
 * {@code CrosshairRenderer} (gated by {@code BlindGateCrosshairRendererMixin}) only while its train HUD is on for a
 * living survival player; otherwise vanilla draws it, whose attack indicator flags an entity in line. While the Blind
 * view is active this outermost wrap (above Wathe's 1000 and the Seeker's 1100) draws the same plain reticle on every
 * path, so SparkTraits' Forced Melee HEAD fallback on this method never runs either.
 * 原版路径上的 C6 准星闸门：Wathe 对 {@code InGameHud.renderCrosshair} 的包裹只在存活生存玩家的列车 HUD 开启时才把
 * 准星交给 {@code CrosshairRenderer}（由 {@code BlindGateCrosshairRendererMixin} 把关），否则由原版绘制，而原版的
 * 攻击指示器会标出视线上的实体。盲人视图生效期间，本最外层包裹（高于 Wathe 的 1000 与搜寻者的 1100）在所有路径上都
 * 只画同一个普通准星，SparkTraits 强制近战在此方法上的 HEAD 回退也因此不会执行。
 */
@Mixin(value = InGameHud.class, priority = 2100)
public abstract class BlindGateInGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @WrapMethod(method = "renderCrosshair")
    private void sparkwitch$blindPlainVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                                       Operation<Void> original) {
        if (BlindClientGates.viewActive()) {
            BlindCrosshair.render(client, context);
            return;
        }
        original.call(context, tickCounter);
    }
}
