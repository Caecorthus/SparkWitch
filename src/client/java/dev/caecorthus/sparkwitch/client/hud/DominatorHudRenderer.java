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
 * Every state uses the Dominator's green; cooldown outranks the other hints, and the prompt is hidden at zero uses
 * (the skill cannot be used then unless it is still cooling down).
 * 支配者右下角单行提示：状态文案加括号内的剩余次数，全部使用支配者的绿色。冷却优先于其他提示；
 * 次数为 0 且不在冷却时隐藏整行（此时技能无法使用）。
 */
public final class DominatorHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int GREEN = 0xAA000000 | DominatorRules.COLOR;

    private DominatorHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(player);
        int remainingUses = component.getRemainingUses();
        int cooldownTicks = component.getCooldownTicks();

        Text hint;
        int color;
        if (cooldownTicks > 0) {
            // Cooldown outranks every other hint, even at zero uses.
            // 冷却优先于其他提示（即使次数为 0）。
            hint = Text.translatable(
                    "hud.sparkwitch.dominator.cooldown",
                    (cooldownTicks + 19) / 20,
                    remainingUses
            );
            color = GREEN;
        } else {
            // No uses left means G does nothing; hide the whole line.
            // 次数为 0 时 G 已无效，隐藏整行。
            if (remainingUses <= 0) {
                return;
            }
            hint = hintText(player, component, remainingUses);
            color = pulseColor();
        }

        context.drawTextWithShadow(
                renderer,
                hint,
                Math.max(0, context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(hint)),
                context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight,
                color
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

    /** The Assassin hint's pulsing alpha, in the Dominator's green. / 刺客提示的呼吸透明度，换成支配者的绿色。 */
    private static int pulseColor() {
        float time = (float) (System.currentTimeMillis() % 2000L) / 2000.0F;
        float pulse = (float) (Math.sin(time * Math.PI * 2.0F) * 0.15D + 0.75D);
        int alpha = (int) (170.0F * pulse);
        return (alpha << 24) | DominatorRules.COLOR;
    }
}
