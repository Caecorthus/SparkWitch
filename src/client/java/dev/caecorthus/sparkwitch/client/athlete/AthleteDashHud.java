package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.text.HudFontOptions;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteComponent;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.Objects;

/**
 * Draws the Athlete's owner-only Dash line ({@link AthleteDashHudRules}). Reads only the local player's real synced
 * role and its owner-only {@link AthleteComponent} ready tick, counted down against the client world clock for display;
 * the server alone decides a dash. Called by {@link AthleteClient} only on a confirmed SparkWitch server. Role-owned
 * presentation: never the action bar and never the {@code gui.sparkwitch.skills} panel.
 * 绘制运动员仅本人可见的冲刺行（{@link AthleteDashHudRules}）。只读取本地玩家同步的真实职业及其仅本人同步的
 * {@link AthleteComponent} 就绪刻，并依据客户端世界时钟倒数用于展示；冲刺是否生效只由服务端决定。仅在已确认的
 * SparkWitch 服务器上由 {@link AthleteClient} 调用。属于职业自有展示：绝不使用动作栏，也绝不进入
 * {@code gui.sparkwitch.skills} 面板。
 */
public final class AthleteDashHud {
    // Render-thread cache: the Texts and widths are a pure function of the line, the ability key text, the active
    // Language (replaced on every resource reload) and the font renderer, so they are rebuilt only when one changes.
    // 渲染线程缓存：文本与宽度只取决于该行、技能按键文本、当前 Language（每次资源重载都会换新）与字体渲染器，
    // 因此只在其中之一变化时重建。
    // The key also holds the font glyph options (`HudFontOptions`), which a toggle changes without a reload.
    // 键还包含字形选项（`HudFontOptions`），切换它们不会触发资源重载。
    private static AthleteDashHudRules.Line cachedLine;
    private static Text cachedKeyText;
    private static Language cachedLanguage;
    private static TextRenderer cachedRenderer;
    private static int cachedFontOptions = -1;
    private static Text cachedText;
    private static int cachedTextWidth;
    private static int cachedMeasured;

    private AthleteDashHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the real-role check (the Black Raven can never disguise
        // as the Athlete, so the raw role is the only one that counts).
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回（黑羽鸦永远不能伪装成运动员，因此只认原始职业）。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean athlete = AthleteRules.isAthlete(game.getRole(player));
        if (!athlete) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!AthleteDashHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, athlete,
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        AthleteDashHudRules.Line line = AthleteDashHudRules.line(
                AthleteComponent.KEY.get(player).dashCooldownRemaining(client.world.getTime()));
        // The corner row is taken by the shared witch skill line (defensive: an Athlete has no witch skill id) or by
        // the Saint's Karma line, which any killer of a Hellfire Saint can carry, drawn flush in the corner.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：运动员没有魔女技能 id），或被圣徒业报行占用（任何在地狱火
        // 期间击杀圣徒的人都可能带有，紧贴角落绘制）。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        Text keyText = line.keyHint() ? SparkWitchClient.abilityKeyText() : null;
        Language language = Language.getInstance();
        int fontOptions = HudFontOptions.current();
        if (!line.equals(cachedLine) || !Objects.equals(keyText, cachedKeyText) || language != cachedLanguage
                || renderer != cachedRenderer
                || fontOptions != cachedFontOptions) {
            cachedText = text(line, line.seconds(), keyText);
            cachedTextWidth = renderer.getWidth(cachedText);
            cachedMeasured = renderer.getWidth(text(line, AthleteDashHudRules.measuredSeconds(line.seconds()), keyText));
            cachedLine = line;
            cachedKeyText = keyText;
            cachedLanguage = language;
            cachedRenderer = renderer;
            cachedFontOptions = fontOptions;
        }
        Text text = cachedText;
        int measured = cachedMeasured;
        int width = context.getScaledWindowWidth();
        int x = AthleteDashHudRules.rowX(width, cachedTextWidth);
        int y = AthleteDashHudRules.rowY(width, context.getScaledWindowHeight(), measured, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, text, x, y, line.color());
    }

    private static Text text(AthleteDashHudRules.Line line, int seconds, Text keyText) {
        if (line.keyHint()) {
            return Text.translatable(line.key(), keyText);
        }
        return line.hasSeconds() ? Text.translatable(line.key(), seconds) : Text.translatable(line.key());
    }
}
