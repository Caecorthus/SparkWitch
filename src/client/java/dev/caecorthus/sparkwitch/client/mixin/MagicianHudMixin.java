package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.magician.MagicianStatusHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MagicianHudMixin {
    @Inject(method="renderMainHud", at=@At("TAIL"))
    private void sparkwitch$renderMagicianHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) { if(MinecraftClient.getInstance().player!=null) MagicianStatusHudRenderer.render(context,MinecraftClient.getInstance().player); }
}
