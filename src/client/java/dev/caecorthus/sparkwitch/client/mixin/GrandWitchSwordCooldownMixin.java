package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchSwordHud;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.gui.CooldownRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the sword's slot badge (kill glyph and seconds plus five dash pips) above whichever hotbar or off-hand slot
 * holds it. Wathe's own number is cancelled only while the sword is held: it shows the dash timer alone, easily read as
 * the kill timer. Another selected item keeps Wathe's number.
 * 在持剑的物品栏格或副手格上方绘制剑槽标记（剑形与击杀秒数，加 5 个冲刺点）。仅在手持仪礼剑时取消 Wathe 自己的数字
 * （它只有冲刺计时，易被误读为击杀冷却）；选中其他物品时保留 Wathe 数字。
 */
@Mixin(CooldownRenderer.class)
public abstract class GrandWitchSwordCooldownMixin {
    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$drawSwordSlotBadge(TextRenderer renderer, ClientPlayerEntity player,
                                                     DrawContext context, RenderTickCounter tickCounter,
                                                     CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !GrandWitchClientPresentation.isGrandWitch(player)) {
            return;
        }
        if (GameFunctions.isPlayerPlayingAndAlive(player)) {
            GrandWitchSwordHud.renderSlotBadge(context, renderer, player, tickCounter.getTickDelta(true));
        }
        if (player.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())) {
            ci.cancel();
        }
    }
}
