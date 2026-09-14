package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.gui.CooldownRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the sword's ambiguous number with the role HUD's labelled dash timer. / 仅将仪礼剑含义不明的数字替换为身份 HUD 的冲刺计时。 */
@Mixin(CooldownRenderer.class)
public abstract class GrandWitchSwordCooldownMixin {
    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$useLabelledSwordTimers(TextRenderer renderer, ClientPlayerEntity player,
                                                         DrawContext context, RenderTickCounter tickCounter,
                                                         CallbackInfo ci) {
        if (SparkWitchServerConnection.isConfirmedServer()
                && player.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())
                && GrandWitchClientPresentation.isGrandWitch(player)) {
            ci.cancel();
        }
    }
}
