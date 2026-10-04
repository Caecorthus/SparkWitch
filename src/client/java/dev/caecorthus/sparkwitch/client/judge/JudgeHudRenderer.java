package dev.caecorthus.sparkwitch.client.judge;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Judge-owned bottom-right skill line, drawn from the shared skill-line slot; display only, the server still
 * decides every judgment.
 * 法官自有的右下角技能行，借用共享技能行位置绘制；仅作展示，审判仍由服务端裁决。
 */
public final class JudgeHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private JudgeHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        int balance = PlayerShopComponent.KEY.get(player).getBalance();
        Text line = JudgeClientRules.canAffordJudgment(balance, JudgeRules.JUDGMENT_COST)
                ? Text.translatable("hud.sparkwitch.judge.ready", SparkWitchClient.abilityKeyText(), JudgeRules.JUDGMENT_COST)
                : Text.translatable("hud.sparkwitch.judge.not_enough_money", JudgeRules.JUDGMENT_COST);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(
                renderer,
                line,
                context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line),
                context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight,
                JudgeRules.ROLE_COLOR
        );
    }
}
