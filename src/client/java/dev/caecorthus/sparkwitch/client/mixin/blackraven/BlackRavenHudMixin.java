package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenHudRenderer;
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

/** Thin HUD seam for Black Raven's role-owned mode row. / 黑羽鸦角色自有模式行的薄 HUD 接口。 */
@Mixin(InGameHud.class)
public abstract class BlackRavenHudMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkwitch$renderBlackRavenMode(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            BlackRavenHudRenderer.render(context, player);
        }
    }
}
