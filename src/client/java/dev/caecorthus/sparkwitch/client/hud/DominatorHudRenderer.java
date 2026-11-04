package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * One bottom-right line for the Dominator: the state hint with the remaining uses in parentheses.
 * The line is always visible in the Dominator's opaque green, never pulsing, and keeps showing at zero uses.
 * 支配者右下角单行提示：状态文案加括号内的剩余次数。整行常驻显示、支配者绿色且完全不透明、不做呼吸闪烁；
 * 次数为 0 时也照样显示。
 */
public final class DominatorHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int GREEN = 0xFF000000 | DominatorRules.COLOR;

    private DominatorHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(player);
        int remainingUses = component.getRemainingUses();
        int cooldownTicks = component.getCooldownTicks();

        Text hint;
        if (cooldownTicks > 0) {
            // Cooldown outranks every other hint. / 冷却优先于其他提示。
            hint = Text.translatable(
                    "hud.sparkwitch.dominator.cooldown",
                    (cooldownTicks + 19) / 20,
                    remainingUses
            );
        } else {
            hint = hintText(player, component, remainingUses);
        }

        context.drawTextWithShadow(
                renderer,
                hint,
                Math.max(0, context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(hint)),
                context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight,
                GREEN
        );
    }

    private static Text hintText(ClientPlayerEntity player, DominatorPlayerComponent component, int remainingUses) {
        if (component.hasActiveMark()) {
            return Text.translatable(
                    "hud.sparkwitch.dominator.force_shoot",
                    SparkWitchClient.abilityKeyText(),
                    remainingUses
            );
        }
        PlayerEntity target = DominatorRules.findAimedPlayer(player);
        if (target == null) {
            return Text.translatable("hud.sparkwitch.dominator.no_target", remainingUses);
        }
        return Text.translatable(
                "hud.sparkwitch.dominator.mark",
                SparkWitchClient.abilityKeyText(),
                target.getDisplayName(),
                remainingUses
        );
    }
}
