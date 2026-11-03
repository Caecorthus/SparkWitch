package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 拾荒者 readout.
 * <p>While searching: the percentage sits above the action bar, with a progress bar in the role's identity colour
 * right underneath it. Otherwise the bottom-right skill line carries the key hint, or the remaining cooldown — the
 * cooldown belongs with the skill information, not in an action-bar toast.
 * 拾荒者的信息显示。
 * <p>搜刮过程中：百分比显示在动作栏上方，正下方是用身份色画的进度条。
 * 其余时候右下角的技能行显示按键提示，或剩余冷却——冷却属于技能信息，不占用动作栏。
 */
public final class JunkerHudRenderer {

    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 6;

    /** Height of the progress bar under the percentage text. 百分比文本下方进度条的高度。 */
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_WIDTH = 120;

    /** The action bar sits around {@code height - 72}; keep the readout above it. 动作栏大约在 height-72，把信息放在它上方。 */
    private static final int ACTION_BAR_CLEARANCE = 96;

    private JunkerHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !JunkerRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        JunkerPlayerComponent component = JunkerPlayerComponent.KEY.get(player);
        if (component.isSearching()) {
            renderSearchOverlay(context, client.textRenderer, component);
            return;
        }
        Text line = component.getCooldownSeconds() > 0
                ? Text.translatable("tip.sparkwitch.ragpicker.cooldown", component.getCooldownSeconds())
                        .formatted(Formatting.GRAY)
                : Text.translatable("tip.sparkwitch.ragpicker.ready",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.GRAY);
        int width = client.textRenderer.getWidth(line);
        int x = context.getScaledWindowWidth() - MARGIN - width;
        int y = context.getScaledWindowHeight() - MARGIN - LINE_HEIGHT;
        context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFFF);
    }

    /** Percentage above the action bar, identity-coloured bar right below it. 动作栏上方的百分比，下方是身份色进度条。 */
    private static void renderSearchOverlay(DrawContext context, TextRenderer renderer,
                                            JunkerPlayerComponent component) {
        int centerX = context.getScaledWindowWidth() / 2;
        int textY = context.getScaledWindowHeight() - ACTION_BAR_CLEARANCE;
        Text text = Text.translatable("tip.sparkwitch.ragpicker.searching",
                (int) (component.getProgress() * 100));
        context.drawCenteredTextWithShadow(renderer, text, centerX, textY, 0xFFFFFF);

        int barX = centerX - BAR_WIDTH / 2;
        int barY = textY + renderer.fontHeight + 2;
        // Backing plate, then the fill in the role colour. / 先底面，再用身份色填进度。
        context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0x99000000);
        int filled = (int) (BAR_WIDTH * component.getProgress());
        if (filled > 0) {
            context.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, 0xFF000000 | JunkerRules.COLOR);
        }
    }
}
