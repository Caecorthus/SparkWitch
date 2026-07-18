package dev.caecorthus.sparkwitch.client.mixin.saboteur;

import dev.caecorthus.sparkwitch.client.saboteur.SaboteurHudRenderer;
import dev.caecorthus.sparkwitch.client.saboteur.SaboteurHudRules;
import dev.caecorthus.sparkwitch.client.wraith.WraithClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
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

/** Thin HUD seam for the active promoted Saboteur only. / 仅为活动中的升变破坏者提供薄 HUD 接口。 */
@Mixin(InGameHud.class)
public abstract class SaboteurHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderSaboteurHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        boolean exactSaboteurRole = role != null && SaboteurRole.ID.equals(role.identifier());
        if (SaboteurHudRules.shouldRender(
                SparkWitchServerConnection.isConfirmedServer(),
                exactSaboteurRole,
                WraithClientState.isPromoted(player)
        )) {
            SaboteurHudRenderer.render(context, player);
        }
    }
}
