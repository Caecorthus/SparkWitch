package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attack recharge is independent from kill and dash timers, even under Wathe's custom crosshair. / Wathe 自定义准星也独立显示攻击蓄力，不与击杀及冲刺冷却混用。 */
@Mixin(CrosshairRenderer.class)
public abstract class GrandWitchSwordCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private static void sparkwitch$renderSwordAttackRecharge(MinecraftClient client, ClientPlayerEntity player,
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
        int x = context.getScaledWindowWidth() / 2 - 5;
        int y = context.getScaledWindowHeight() / 2 + 6;
        float progress = MathHelper.clamp(player.getAttackCooldownProgress(tickCounter.getTickDelta(true)), 0, 1);
        boolean killReady = GrandWitchRuntimeComponent.KEY.get(player).getSwordKillCooldownTicks() <= 0;
        context.fill(x, y, x + 10, y + 2, 0xAA222222);
        context.fill(x, y, x + (int) (progress * 10), y + 2, killReady ? 0xFFF2DFF7 : 0xFF888888);
    }
}
