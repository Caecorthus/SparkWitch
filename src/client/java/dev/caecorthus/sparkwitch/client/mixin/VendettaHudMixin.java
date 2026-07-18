package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the promoted Vendetta's private reveal timer. / 追加晋升仇杀客仅本人可见的透视计时。 */
@Mixin(InGameHud.class)
public abstract class VendettaHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderVendettaHud(
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (VendettaClientPresentation.hasActiveOwnerState(player)) {
            VendettaHudRenderer.render(context, player);
        }
    }
}
