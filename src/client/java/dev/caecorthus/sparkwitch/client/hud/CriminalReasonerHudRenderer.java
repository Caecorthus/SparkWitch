package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.criminalreasoner.CriminalReasonerClientModule;
import dev.caecorthus.sparkwitch.client.criminalreasoner.CriminalReasonerHudRules;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Bottom-right reasoning readout, three lines as in the source HUD:
 * <ol>
 *   <li>the ability-key hint, replaced by "reasoning available in X s" while cooling down;</li>
 *   <li>reasoning progress;</li>
 *   <li>the invisibility hint with the charges left, replaced by the countdown while invisible.</li>
 * </ol>
 * Lines, placement and the display gate live in {@link CriminalReasonerHudRules}; the acting gate (confirmed server,
 * ACTIVE round, exact role, alive, not spectating, not swallowed) is
 * {@link CriminalReasonerClientModule#isActiveReasoner}, the same one that closes the reasoning menu.
 * 右下角的推理信息，按原版 HUD 的三行：
 * <ol>
 *   <li>技能键提示，冷却中替换为“推理将在 X 秒后可用”；</li>
 *   <li>推理进度；</li>
 *   <li>隐身提示与剩余次数，隐身中替换为倒计时。</li>
 * </ol>
 * 行内容、位置与显示门槛见 {@link CriminalReasonerHudRules}；行动门槛（已确认服务器、ACTIVE 回合、精确职业、存活、非旁观、
 * 未被吞下）为 {@link CriminalReasonerClientModule#isActiveReasoner}，与关闭推理菜单的门槛相同。
 */
public final class CriminalReasonerHudRenderer {
    // Render-thread caches, one per row (see CachedHudText). / 渲染线程缓存，每行一个（见 CachedHudText）。
    private static final CachedHudText<CriminalReasonerHudRules.Line> REASONING = cache();
    private static final CachedHudText<CriminalReasonerHudRules.Line> PROGRESS = cache();
    private static final CachedHudText<CriminalReasonerHudRules.Line> INVISIBILITY = cache();

    private CriminalReasonerHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the real-role check.
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        if (!CriminalReasonerRules.isReasoner(game.getRole(player))) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        if (!CriminalReasonerHudRules.showsHud(client.options.hudHidden, game.getFade(), trainHudActive,
                CriminalReasonerClientModule.isActiveReasoner(client))) {
            return;
        }
        // Fail closed: no synced component, no rows. / 失败即关闭：没有同步组件就不画。
        CriminalReasonerComponent state = CriminalReasonerComponent.KEY.getNullable(player);
        if (state == null) {
            return;
        }
        StatusEffectInstance invisibility = player.getStatusEffect(StatusEffects.INVISIBILITY);
        CriminalReasonerHudRules.Line reasoning = CriminalReasonerHudRules.reasoningLine(state.getCooldownSeconds());
        CriminalReasonerHudRules.Line progress = CriminalReasonerHudRules.progressLine(
                state.getSuccessfulReasoningCount(), state.getRequiredReasoningCount());
        CriminalReasonerHudRules.Line invisible = CriminalReasonerHudRules.invisibilityLine(
                invisibility == null ? 0 : invisibility.getDuration(), state.getInvisibilityCharges());

        TextRenderer renderer = client.textRenderer;
        REASONING.update(reasoning, keyText(reasoning), renderer);
        PROGRESS.update(progress, null, renderer);
        INVISIBILITY.update(invisible, keyText(invisible), renderer);
        int width = context.getScaledWindowWidth();
        // All three rows are measured together, so a wide row lifts the whole block.
        // 三行一起测量，因此较宽的一行会让整组一同抬升。
        int measured = Math.max(REASONING.measuredWidth(),
                Math.max(PROGRESS.measuredWidth(), INVISIBILITY.measuredWidth()));
        int bottomY = CriminalReasonerHudRules.bottomRowY(width, context.getScaledWindowHeight(), measured,
                renderer.fontHeight);
        draw(context, renderer, INVISIBILITY, invisible, width, bottomY, 0);
        draw(context, renderer, PROGRESS, progress, width, bottomY, 1);
        draw(context, renderer, REASONING, reasoning, width, bottomY, 2);
    }

    private static void draw(DrawContext context, TextRenderer renderer,
                             CachedHudText<CriminalReasonerHudRules.Line> row, CriminalReasonerHudRules.Line line,
                             int width, int bottomY, int index) {
        context.drawTextWithShadow(renderer, row.text(), CriminalReasonerHudRules.rowX(width, row.width()),
                CriminalReasonerHudRules.rowY(bottomY, renderer.fontHeight, index), line.color());
    }

    private static @Nullable Text keyText(CriminalReasonerHudRules.Line line) {
        return switch (line.argument()) {
            case KEY_HINT -> SparkWitchClient.abilityKeyText();
            case CHARGES -> SecondaryAbilityController.secondaryKeyText();
            default -> null;
        };
    }

    private static CachedHudText<CriminalReasonerHudRules.Line> cache() {
        return new CachedHudText<>(CriminalReasonerHudRenderer::text, CriminalReasonerHudRules.Line::value,
                CriminalReasonerHudRules::measuredValue);
    }

    private static Text text(CriminalReasonerHudRules.Line line, int value, @Nullable Text keyText) {
        return switch (line.argument()) {
            case KEY_HINT -> Text.translatable(line.key(), keyText);
            case SECONDS -> Text.translatable(line.key(), value);
            case PROGRESS -> Text.translatable(line.key(), value, line.total());
            case CHARGES -> Text.translatable(line.key(), keyText, value);
        };
    }
}
