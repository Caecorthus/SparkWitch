package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 橘雪莉 readout, two lines as specified: deduction readiness (or its cooldown), then Mighty Force
 * (ready / active with seconds left / cooling down).
 * 橘雪莉的两行信息：推演就绪（或冷却），以及巨力（就绪 / 激活剩余秒数 / 冷却）。
 */
public final class SherryHudRenderer {

    private static final int LINE_HEIGHT = 10;
    /** Mighty Force's own colour, as in the apprentice ability. 与预备魔女巨力一致的颜色。 */
    private static final int MIGHTY_COLOR = 0xFF000000 | dev.caecorthus.sparkwitch.roles.civilian.apprentice
            .abilities.MightyForce.MightyForceAbility.COLOR;
    /** Mana glyph, the same private-use character the mana row uses. 与魔力行相同的私有区图标字符。 */
    private static final String MANA_GLYPH = "\ue782 ";
    private static final int MARGIN = 6;

    private SherryHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !SherryRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        SherryComponent component = SherryComponent.KEY.get(player);
        int bottom = context.getScaledWindowHeight() - MARGIN;

        // Bottom line: Mighty Force. 最下面一行：巨力。
        Text mighty;
        if (component.getMightyActiveSeconds() > 0) {
            mighty = Text.translatable("hud.sparkwitch.sherry.mighty_active",
                    component.getMightyActiveSeconds()).formatted(Formatting.AQUA);
        } else if (component.getMightyCooldownSeconds() > 0) {
            mighty = Text.translatable("hud.sparkwitch.sherry.mighty_cooldown",
                    component.getMightyCooldownSeconds()).formatted(Formatting.GRAY);
        } else {
            mighty = Text.translatable("hud.sparkwitch.sherry.mighty",
                    SecondaryAbilityController.secondaryKeyText()).formatted(Formatting.WHITE);
        }
        bottom = drawRight(context, Text.literal(MANA_GLYPH).append(mighty), bottom, MIGHTY_COLOR);

        // Top line: deduction. 上面一行：推演。
        Text deduction = component.getDeductionCooldownSeconds() > 0
                ? Text.translatable("hud.sparkwitch.sherry.deduction_cooldown",
                        component.getDeductionCooldownSeconds()).formatted(Formatting.GRAY)
                : Text.translatable("hud.sparkwitch.sherry.deduction",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.GOLD);
        drawRight(context, deduction, bottom);
    }

    private static int drawRight(DrawContext context, Text line, int bottom) {
        return drawRight(context, line, bottom, 0xFFFFFF);
    }

    private static int drawRight(DrawContext context, Text line, int bottom, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.textRenderer.getWidth(line);
        int x = context.getScaledWindowWidth() - MARGIN - width;
        int y = bottom - LINE_HEIGHT;
        context.drawTextWithShadow(client.textRenderer, line, x, y, color);
        return y - 2;
    }
}
