package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Renders the active witch skill status in the same bottom-right area as wathe role abilities.
 * 在右下角显示魔女主动技能状态，位置和 wathe / NoellesRoles 的技能提示保持一致。
 */
public final class WitchSkillHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private WitchSkillHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }

        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        if (skillId == null) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text line = stateText(component, skillId);
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, WitchSkillClientTexts.color(skillId));
    }

    private static Text stateText(WitchPlayerComponent component, Identifier skillId) {
        int activeTicks = component.getActiveSkillWindowTicks();
        if (activeTicks > 0) {
            return Text.translatable(
                    "hud.sparkwitch.skill.active",
                    WitchSkillClientTexts.name(skillId),
                    seconds(activeTicks)
            );
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.skill.cooldown",
                    WitchSkillClientTexts.name(skillId),
                    seconds(component.getCooldownTicks())
            );
        }
        return Text.translatable(
                "hud.sparkwitch.skill.ready",
                WitchSkillClientTexts.name(skillId),
                SparkWitchClient.abilityKeyText()
        );
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}
