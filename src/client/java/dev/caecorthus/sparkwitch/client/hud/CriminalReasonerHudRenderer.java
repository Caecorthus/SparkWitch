package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Bottom-right reasoning readout, three lines as in the source HUD:
 * <ol>
 *   <li>the ability-key hint, replaced by "reasoning available in X s" while cooling down;</li>
 *   <li>reasoning progress;</li>
 *   <li>the invisibility hint with the charges left, replaced by the countdown while invisible.</li>
 * </ol>
 * 右下角的推理信息，按原版 HUD 的三行：
 * <ol>
 *   <li>技能键提示，冷却中替换为“推理将在 X 秒后可用”；</li>
 *   <li>推理进度；</li>
 *   <li>隐身提示与剩余次数，隐身中替换为倒计时。</li>
 * </ol>
 */
public final class CriminalReasonerHudRenderer {

    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 6;

    private CriminalReasonerHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !CriminalReasonerRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(player);
        int bottom = context.getScaledWindowHeight() - MARGIN;

        // Drawn bottom-up: line 3, then 2, then 1. / 自下而上画：先第 3 行，再第 2 行、第 1 行。
        var invisibility = player.getStatusEffect(StatusEffects.INVISIBILITY);
        bottom = drawRight(context, invisibility != null
                ? Text.translatable("hud.sparkwitch.criminal_reasoner.invisibility_active",
                        invisibility.getDuration() / 20).formatted(Formatting.AQUA)
                : Text.translatable("hud.sparkwitch.criminal_reasoner.invisibility",
                        SecondaryAbilityController.secondaryKeyText(),
                        component.getInvisibilityCharges()).formatted(Formatting.GRAY), bottom);
        bottom = drawRight(context, Text.translatable("hud.sparkwitch.criminal_reasoner.progress",
                component.getSuccessfulReasoningCount(),
                component.getRequiredReasoningCount()).formatted(Formatting.GOLD), bottom);
        drawRight(context, component.getCooldownSeconds() > 0
                ? Text.translatable("hud.sparkwitch.criminal_reasoner.cooldown",
                        component.getCooldownSeconds()).formatted(Formatting.GRAY)
                : Text.translatable("hud.sparkwitch.criminal_reasoner.press_key",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.WHITE), bottom);
    }

    private static int drawRight(DrawContext context, Text line, int bottom) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.textRenderer.getWidth(line);
        int x = context.getScaledWindowWidth() - MARGIN - width;
        int y = bottom - LINE_HEIGHT;
        context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFFF);
        return y - 2;
    }
}
