package dev.caecorthus.sparkwitch.client.mixin.kidnapper;

import dev.caecorthus.sparkwitch.client.kidnapper.KidnapperControlledHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在完整 HUD 绘制结束后覆盖迷药控制者的画面。 */
@Mixin(InGameHud.class)
public abstract class KidnapperControlledHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderControlledHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            KidnapperControlledHudRenderer.render(context, player);
        }
    }
}
