package dev.caecorthus.sparkwitch.client.consultant;

import dev.caecorthus.sparkwitch.roles.killer.consultant.ConsultantRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Bottom-right skill line for 顾问: flip state, cost and target. 顾问右下角技能行。 */
public final class ConsultantHudRenderer {

    private static final int MARGIN = 10;
    private static final int LINE_HEIGHT = 10;
    private static final int READY_COLOR = ConsultantRules.COLOR;
    private static final int DIM_COLOR = dim(ConsultantRules.COLOR);

    private ConsultantHudRenderer() {
    }

    private static int dim(int color) {
        return ((color >> 16 & 0xFF) * 60 / 100) << 16
                | ((color >> 8 & 0xFF) * 60 / 100) << 8
                | (color & 0xFF) * 60 / 100;
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        int remaining = ConsultantClientModule.remainingCooldownSeconds();
        Text line;
        if (remaining > 0) {
            line = Text.translatable("skill.sparkwitch.consultant_inspect.cooldown", remaining);
        } else {
            Text target = ConsultantClientModule.targetName();
            line = target == null
                    ? Text.translatable("skill.sparkwitch.consultant_inspect.idle", ConsultantRules.INSPECT_COST)
                    : Text.translatable("skill.sparkwitch.consultant_inspect.ready",
                            ConsultantClientModule.keyText(), ConsultantRules.INSPECT_COST, target);
        }
        int x = context.getScaledWindowWidth() - MARGIN - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - MARGIN - LINE_HEIGHT;
        context.drawTextWithShadow(renderer, line, x, y, remaining > 0 ? DIM_COLOR : READY_COLOR);
    }
}
