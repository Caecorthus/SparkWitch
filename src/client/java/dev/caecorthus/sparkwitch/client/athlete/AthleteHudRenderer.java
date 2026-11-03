package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Bottom-right skill line for 运动员: the skill name while ready, a countdown while cooling down.
 * 运动员右下角技能行：可用时显示技能名，冷却时显示倒计时。
 */
public final class AthleteHudRenderer {

    private static final int MARGIN = 10;
    private static final int LINE_HEIGHT = 10;
    /** Role colour for the ready line; the cooldown line is the same hue dimmed. 就绪用身份色，冷却用同色暗化。 */
    private static final int READY_COLOR = AthleteRules.COLOR;
    private static final int COOLDOWN_COLOR = dim(AthleteRules.COLOR);

    private static int dim(int color) {
        int r = (color >> 16 & 0xFF) * 55 / 100;
        int g = (color >> 8 & 0xFF) * 55 / 100;
        int b = (color & 0xFF) * 55 / 100;
        return r << 16 | g << 8 | b;
    }

    private AthleteHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        int remaining = AthleteClientModule.remainingCooldownSeconds();
        // The %s is the player's actual key binding, never a hard-coded key name.
        // %s 是玩家实际的按键绑定，不写死键名。
        Text text = remaining > 0
                ? Text.translatable("skill.sparkwitch.athlete_sprint.cooldown", remaining)
                : Text.translatable("skill.sparkwitch.athlete_sprint.ready",
                        dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge.keyText());
        int color = remaining > 0 ? COOLDOWN_COLOR : READY_COLOR;
        int x = context.getScaledWindowWidth() - MARGIN - renderer.getWidth(text);
        int y = context.getScaledWindowHeight() - MARGIN - LINE_HEIGHT;
        context.drawTextWithShadow(renderer, text, x, y, color);
    }
}
