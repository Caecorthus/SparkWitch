package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hud.WitchManaHudRenderer;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds SparkWitch mana to the vanilla HUD pass without changing Wathe's money renderer.
 * 在原版 HUD 渲染流程中追加 SparkWitch 魔力值，不改动 wathe 的金币渲染器。
 */
@Mixin(InGameHud.class)
public abstract class WitchManaHudMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkwitch$renderManaHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            WitchManaHudRenderer.render(context, player);
        }
    }
}
