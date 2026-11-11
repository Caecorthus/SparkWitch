package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.component.PlannerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Bottom-right panel for 规划大师: the passive next-task countdown and the skill state.
 * 规划大师右下角面板：被动显示下次任务倒计时，以及技能状态。
 */
public final class PlannerHudRenderer {

    private static final int MARGIN = 10;
    private static final int LINE_HEIGHT = 10;
    private static final int READY_COLOR = PlannerRules.COLOR;
    private static final int DIM_COLOR = dim(PlannerRules.COLOR);

    private PlannerHudRenderer() {
    }

    private static int dim(int color) {
        return ((color >> 16 & 0xFF) * 60 / 100) << 16
                | ((color >> 8 & 0xFF) * 60 / 100) << 8
                | (color & 0xFF) * 60 / 100;
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        PlannerPlayerComponent component = PlannerPlayerComponent.KEY.get(player);

        int ticks = component.getNextTaskTimer();
        Text taskLine = ticks < 0
                ? Text.translatable("hud.sparkwitch.planner.task.unknown")
                : Text.translatable("hud.sparkwitch.planner.task", Math.max(0, ticks / 20));
        // The ready line only exists while a target is under the crosshair.
        // 只有在准星瞄准到玩家时才显示“就绪”这一行。
        Text skillLine;
        if (!PlannerClientModule.isReady()) {
            skillLine = Text.translatable("skill.sparkwitch.planner_recommend.cooldown",
                    PlannerClientModule.remainingCooldownSeconds());
        } else {
            Text target = PlannerClientModule.targetName();
            skillLine = target == null
                    ? Text.translatable("skill.sparkwitch.planner_recommend.idle", PlannerRules.SKILL_COST)
                    : Text.translatable("skill.sparkwitch.planner_recommend.ready", target, PlannerRules.SKILL_COST);
        }

        int x = context.getScaledWindowWidth() - MARGIN;
        int y = context.getScaledWindowHeight() - MARGIN - LINE_HEIGHT * 2;
        context.drawTextWithShadow(renderer, taskLine, x - renderer.getWidth(taskLine), y, DIM_COLOR);
        context.drawTextWithShadow(renderer, skillLine, x - renderer.getWidth(skillLine), y + LINE_HEIGHT,
                PlannerClientModule.isReady() ? READY_COLOR : DIM_COLOR);
    }
}
