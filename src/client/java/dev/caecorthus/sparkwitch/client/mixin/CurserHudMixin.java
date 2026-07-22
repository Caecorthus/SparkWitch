package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.curser.CurserHudRenderer;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thin HUD seam for the active promoted Curser only. / 仅为活动中的晋升诅咒师提供薄 HUD 接口。 */
@Mixin(InGameHud.class)
public abstract class CurserHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderCurserHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null
                && SparkWitchServerConnection.isConfirmedServer()
                && WraithClientState.isPromoted(player)
                && GameWorldComponent.KEY.get(player.getWorld()).getRole(player) == SparkWitchRoles.curser()) {
            CurserHudRenderer.render(context, player);
        }
    }
}
