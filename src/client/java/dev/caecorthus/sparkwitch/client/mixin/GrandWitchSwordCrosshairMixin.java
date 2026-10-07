package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchSwordHud;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the sword's own marks to Wathe's custom crosshair: attack recharge underneath, kill cooldown glyph on the left,
 * dash cooldown chevrons on the right. Hidden with the crosshair outside first person.
 * 在 Wathe 自定义准星上加入仪礼剑标记：下方攻击蓄力，左侧击杀冷却剑形，右侧冲刺冷却双箭头。非第一人称时随准星隐藏。
 */
@Mixin(CrosshairRenderer.class)
public abstract class GrandWitchSwordCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private static void sparkwitch$renderSwordCooldowns(MinecraftClient client, ClientPlayerEntity player,
                                                       DrawContext context, RenderTickCounter tickCounter,
                                                       CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || client.options.hudHidden
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !client.options.getPerspective().isFirstPerson()
                || !player.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())
                || !GrandWitchClientPresentation.isGrandWitch(player)) {
            return;
        }
        GrandWitchSwordHud.renderCrosshair(context, player, tickCounter.getTickDelta(true));
    }
}
