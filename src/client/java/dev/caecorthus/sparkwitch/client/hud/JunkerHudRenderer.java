package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStatusHudRules;
import dev.caecorthus.sparkwitch.client.junker.JunkerClientModule;
import dev.caecorthus.sparkwitch.client.junker.JunkerHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 拾荒者 owner-only readout ({@link JunkerHudRules}), drawn from a {@code HudRenderCallback} registered by
 * {@link JunkerClientModule}, like the other role-owned lines. While searching: the percentage with the identity-coloured
 * bar under it, above the action bar. Otherwise: the corner skill line with the key hint or the remaining cooldown.
 * Reads only the local player's own synced role and owner-synced {@code sparkwitch:ragpicker_player} state.
 * 拾荒者仅本人可见的信息显示（{@link JunkerHudRules}），与其他职业自有提示行一样由 {@link JunkerClientModule} 注册的
 * {@code HudRenderCallback} 绘制。搜刮中：动作栏上方显示百分比及其下方的身份色进度条；其余时候：右下角技能行显示按键
 * 提示或剩余冷却。只读取本地玩家自己同步的身份与仅同步给本人的 {@code sparkwitch:ragpicker_player} 状态。
 */
public final class JunkerHudRenderer {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKING_COLOR = 0x99000000;

    private JunkerHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !JunkerClientModule.isActiveRagpicker(player)) {
            return;
        }
        JunkerPlayerComponent component = JunkerPlayerComponent.KEY.get(player);
        if (component.isSearching()) {
            renderSearchReadout(context, client.textRenderer, player, component.getProgress());
            return;
        }
        renderCornerLine(context, client.textRenderer,
                JunkerHudRules.cornerLine(component.searchCooldownRemainingTicks(client.world.getTime())));
    }

    private static void renderCornerLine(DrawContext context, TextRenderer renderer, JunkerHudRules.Line line) {
        Text text = cornerText(line, line.seconds());
        Text measured = line.hasSeconds() ? cornerText(line, JunkerHudRules.measuredSeconds(line.seconds())) : text;
        int width = context.getScaledWindowWidth();
        int x = JunkerHudRules.cornerX(width, renderer.getWidth(text));
        int y = JunkerHudRules.cornerY(width, context.getScaledWindowHeight(), renderer.getWidth(measured),
                renderer.fontHeight);
        context.drawTextWithShadow(renderer, text, x, y, TEXT_COLOR);
    }

    private static Text cornerText(JunkerHudRules.Line line, int seconds) {
        MutableText text = line.keyHint()
                ? Text.translatable(line.key(), SparkWitchClient.abilityKeyText())
                : Text.translatable(line.key(), seconds);
        return text.formatted(Formatting.GRAY);
    }

    /**
     * Centred in the shared "above the action bar" band; while the Control Expert status block can show (the local
     * player is stunned or disrupted) the readout sits above that block's tallest form instead.
     * 居中于共享的“动作栏上方”显示带；控场专家状态块可能显示时（本地玩家被眩晕或干扰），读数改为位于该块最高形态的上方。
     */
    private static void renderSearchReadout(DrawContext context, TextRenderer renderer, ClientPlayerEntity player,
                                            float progress) {
        ControlExpertStatusComponent status = ControlExpertStatusComponent.KEY.getNullable(player);
        int statusLines = status == null ? 0
                : ControlExpertStatusHudRules.maxLineCount(status.isStunned(), status.isDisrupted());
        int bandTop = ControlExpertStatusHudRules.blockTop(context.getScaledWindowHeight(), renderer.fontHeight,
                statusLines);
        int centerX = context.getScaledWindowWidth() / 2;
        int textY = JunkerHudRules.searchTextY(bandTop, renderer.fontHeight);
        Text text = Text.translatable(JunkerHudRules.SEARCHING_KEY, JunkerHudRules.percent(progress));
        context.drawCenteredTextWithShadow(renderer, text, centerX, textY, TEXT_COLOR);

        int barX = centerX - JunkerHudRules.BAR_WIDTH / 2;
        int barY = JunkerHudRules.barY(textY, renderer.fontHeight);
        int border = JunkerHudRules.BAR_BORDER;
        // Backing plate, then the fill in the role colour. / 先底板，再用身份色填进度。
        context.fill(barX - border, barY - border, barX + JunkerHudRules.BAR_WIDTH + border,
                barY + JunkerHudRules.BAR_HEIGHT + border, BACKING_COLOR);
        int filled = JunkerHudRules.filledWidth(progress);
        if (filled > 0) {
            context.fill(barX, barY, barX + filled, barY + JunkerHudRules.BAR_HEIGHT, 0xFF000000 | JunkerRules.COLOR);
        }
    }
}
