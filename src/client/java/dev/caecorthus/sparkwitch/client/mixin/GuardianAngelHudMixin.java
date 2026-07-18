package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.client.guardianangel.GuardianAngelHudRenderer;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.doctor4t.wathe.api.Role;
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

/** Adds the promoted Guardian Angel's private primary-skill line. / 追加晋升守护天使仅本人可见的主技能提示。 */
@Mixin(InGameHud.class)
public abstract class GuardianAngelHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderGuardianAngelHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !SparkWitchApi.isWraithActive(player)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (GuardianAngelRules.isGuardianAngel(role)) {
            GuardianAngelHudRenderer.render(context, player);
        }
    }
}
