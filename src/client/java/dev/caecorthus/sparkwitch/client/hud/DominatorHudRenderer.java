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
 * Two bottom-right lines for the Dominator, stacked like the Assassin HUD: the state hint on top and the remaining
 * uses below it. Hint colour is the Assassin's pulsing gold; the uses line is green while available and red at zero.
 * 支配者右下角两行，堆叠方式与刺客 HUD 相同：状态提示在上、剩余次数在下。提示色沿用刺客的金色呼吸效果，
 * 次数行有剩余时为绿色、为 0 时红色。
 */
public final class DominatorHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int ROW_GAP = 2;
    private static final int USES_AVAILABLE_COLOR = 0xAA00FF00;
    private static final int USES_EMPTY_COLOR = 0xAAFF0000;
    /** The Assassin HUD's cooldown gold. / 刺客 HUD 的冷却金色。 */
    private static final int COOLDOWN_COLOR = 0xAAFFC800;

    private DominatorHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(player);
        int remainingUses = component.getRemainingUses();
        Text uses = Text.translatable("hud.sparkwitch.dominator.uses", remainingUses);

        int width = context.getScaledWindowWidth();
        int usesY = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(
                renderer,
                uses,
                Math.max(0, width - RIGHT_PADDING - renderer.getWidth(uses)),
                usesY,
                remainingUses > 0 ? USES_AVAILABLE_COLOR : USES_EMPTY_COLOR
        );
        int hintY = usesY - renderer.fontHeight - ROW_GAP;
        int cooldownTicks = component.getCooldownTicks();
        if (cooldownTicks > 0) {
            // Cooldown outranks every other hint, even at zero uses; same gold as the Assassin's cooldown line.
            // 冷却优先于其他提示（即使次数为 0），颜色与刺客冷却行一致。
            Text cooldown = Text.translatable(
                    "hud.sparkwitch.dominator.cooldown",
                    (cooldownTicks + 19) / 20
            );
            context.drawTextWithShadow(
                    renderer,
                    cooldown,
                    Math.max(0, width - RIGHT_PADDING - renderer.getWidth(cooldown)),
                    hintY,
                    COOLDOWN_COLOR
            );
            return;
        }
        // No uses left means G does nothing; hide the whole prompt line and keep only the red uses counter.
        // 次数为 0 时 G 已无效，隐藏整行提示，只保留红色次数。
        if (remainingUses <= 0) {
            return;
        }
        Text hint = hintText(player, component);
        context.drawTextWithShadow(
                renderer,
                hint,
                Math.max(0, width - RIGHT_PADDING - renderer.getWidth(hint)),
                hintY,
                pulseColor()
        );
    }

    private static Text hintText(ClientPlayerEntity player, DominatorPlayerComponent component) {
        if (component.hasActiveMark()) {
            return Text.translatable(
                    "hud.sparkwitch.dominator.force_shoot",
                    SparkWitchClient.abilityKeyText()
            );
        }
        PlayerEntity target = DominatorRules.findAimedPlayer(player);
        if (target == null) {
            return Text.translatable("hud.sparkwitch.dominator.no_target");
        }
        return Text.translatable(
                "hud.sparkwitch.dominator.mark",
                SparkWitchClient.abilityKeyText(),
                target.getDisplayName()
        );
    }

    /** The Assassin hint's pulsing gold. / 刺客提示的金色呼吸色。 */
    private static int pulseColor() {
        float time = (float) (System.currentTimeMillis() % 2000L) / 2000.0F;
        float pulse = (float) (Math.sin(time * Math.PI * 2.0F) * 0.15D + 0.75D);
        int alpha = (int) (170.0F * pulse);
        return (alpha << 24) | 0xFFC800;
    }
}
