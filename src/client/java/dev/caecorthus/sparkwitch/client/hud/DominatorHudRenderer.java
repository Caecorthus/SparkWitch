package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.dominator.DominatorClientModule;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * One bottom-right line for the Dominator, drawn from the {@code HudRenderCallback} that
 * {@link DominatorClientModule} registers: the state hint with the remaining uses in parentheses. The line is always
 * visible in the Dominator's opaque green, never pulsing, and keeps showing at zero uses (owner choice in PR #192).
 * It sits in the shared corner slot (5 px padding, one font row), which {@code SaintHudLayoutRules} reserves so the
 * Karma line stacks above it. Reads only the local player's own synced role and owner-synced state.
 * 支配者右下角单行提示，由 {@link DominatorClientModule} 注册的 {@code HudRenderCallback} 绘制：状态文案加括号内的剩余
 * 次数。整行常驻显示、支配者绿色且完全不透明、不做呼吸闪烁；次数为 0 时也照样显示（PR #192 所有者选择）。它位于共享的
 * 右下角位置（5 像素边距、一行字高），{@code SaintHudLayoutRules} 为其预留，使业障行叠放在其上方。只读取本地玩家自己
 * 同步的身份与仅同步给本人的状态。
 */
public final class DominatorHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int GREEN = 0xFF000000 | DominatorRules.COLOR;

    private DominatorHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !DominatorClientModule.isActiveDominator(player)) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text hint = hintText(player, DominatorPlayerComponent.KEY.get(player));
        context.drawTextWithShadow(
                renderer,
                hint,
                Math.max(0, context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(hint)),
                context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight,
                GREEN
        );
    }

    private static Text hintText(ClientPlayerEntity player, DominatorPlayerComponent component) {
        int remainingUses = component.getRemainingUses();
        if (remainingUses <= 0) {
            // Marking needs a use, so at zero G does nothing: say how to earn one instead of a dead key hint. No mark
            // can be active here (a mark starts only with a use left and only a release spends one).
            // 标记需要至少一次次数，次数为 0 时 G 无效：改为提示如何获得次数，而不是无效的按键提示。此时不可能有进行中的
            // 标记（只有剩余次数时才能开始标记，只有释放才会消耗次数）。
            return Text.translatable("hud.sparkwitch.dominator.no_uses", remainingUses);
        }
        int cooldownTicks = component.getCooldownTicks();
        if (cooldownTicks > 0) {
            // Cooldown outranks the key hints. / 冷却优先于按键提示。
            return Text.translatable(
                    "hud.sparkwitch.dominator.cooldown",
                    TickTime.ceilSeconds(cooldownTicks),
                    remainingUses
            );
        }
        if (component.hasActiveMark()) {
            return Text.translatable(
                    "hud.sparkwitch.dominator.force_shoot",
                    SparkWitchClient.abilityKeyText(),
                    remainingUses
            );
        }
        PlayerEntity target = DominatorClientModule.aimedPlayer(player);
        if (target == null) {
            return Text.translatable("hud.sparkwitch.dominator.no_target", remainingUses);
        }
        return Text.translatable(
                "hud.sparkwitch.dominator.mark",
                SparkWitchClient.abilityKeyText(),
                DominatorClientModule.visibleName(target),
                remainingUses
        );
    }
}
