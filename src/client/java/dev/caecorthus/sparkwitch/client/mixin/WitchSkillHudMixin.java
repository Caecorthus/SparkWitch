package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.CriminologistHudRenderer;
import dev.caecorthus.sparkwitch.client.WitchSkillHudRenderer;
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
 * Adds the SparkWitch active skill line to the main HUD render pass.
 * 在主 HUD 渲染流程中追加 SparkWitch 主动技能提示。
 */
@Mixin(InGameHud.class)
public abstract class WitchSkillHudMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkwitch$renderSkillHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            CriminologistHudRenderer.render(context, player);
            WitchSkillHudRenderer.render(context, player);
        }
    }
}
