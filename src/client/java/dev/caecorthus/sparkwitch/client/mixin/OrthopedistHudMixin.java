package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.hud.OrthopedistHudRenderer;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Orthopedist line without replacing other HUD modules. / 独立叠加骨科大夫提示，不替换其他 HUD 模块。 */
@Mixin(InGameHud.class)
public abstract class OrthopedistHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderOrthopedistHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        if (GameWorldComponent.KEY.get(player.getWorld()).isRole(player, SparkWitchRoles.orthopedist())) {
            OrthopedistHudRenderer.render(context, player);
        }
    }
}
