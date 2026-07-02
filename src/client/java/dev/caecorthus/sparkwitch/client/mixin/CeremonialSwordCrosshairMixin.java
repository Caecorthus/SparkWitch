package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.CeremonialSwordCrosshairRenderer;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds SparkWitch's ceremonial sword attack indicator to Wathe's in-round crosshair renderer.
 * 将 SparkWitch 仪礼剑攻击冷却指示器追加到 wathe 局内准星渲染器。
 */
@Mixin(CrosshairRenderer.class)
public abstract class CeremonialSwordCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private static void sparkwitch$renderCeremonialSwordAttackIndicator(
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        CeremonialSwordCrosshairRenderer.render(client, player, context, tickCounter);
    }
}
