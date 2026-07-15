package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hud.SaintHudRenderer;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintHudLayoutRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Saint and Karma state above, rather than in place of, the current role ability HUD.
 * 将圣徒与业障状态叠放在当前职业技能 HUD 上方，而不是覆盖原提示。
 */
@Mixin(InGameHud.class)
public abstract class SaintHudMixin {
    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderSaintHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }

        List<Text> lines = SaintHudRenderer.getHudLines(player);
        if (lines.isEmpty()) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        Identifier roleId = role == null ? null : role.identifier();
        boolean hasSparkWitchSkill = WitchPlayerComponent.KEY.get(player).getActiveSkillId() != null;
        int reservedHeight = SaintHudLayoutRules.reservedBottomHeight(
                roleId,
                getTextRenderer().fontHeight,
                hasSparkWitchSkill
        );
        for (Text line : lines) {
            int lineHeight = getTextRenderer().getWrappedLinesHeight(line, Integer.MAX_VALUE);
            int drawX = context.getScaledWindowWidth()
                    - getTextRenderer().getWidth(line)
                    - SaintHudLayoutRules.rightPadding(roleId, hasSparkWitchSkill);
            int drawY = SaintHudLayoutRules.drawY(
                    context.getScaledWindowHeight(),
                    lineHeight,
                    reservedHeight
            );
            context.drawTextWithShadow(getTextRenderer(), line, drawX, drawY, SaintRules.COLOR);
            reservedHeight = context.getScaledWindowHeight() - drawY;
        }
    }
}
