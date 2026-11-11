package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.text.HudFontOptions;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
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
 * Draws Hanna's owner-only float line ({@link HannaFloatHudRules}). Display only: it reads the local player's real
 * synced role, her {@link HannaComponent} (the float's remaining ticks counted down locally, and the owner-only ready
 * tick counted down against the client world clock) and her shared mana, and never predicts a float; the server alone
 * decides one. Called by {@link HannaClient} only on a confirmed SparkWitch server. Role-owned presentation: never the
 * action bar and never the {@code gui.sparkwitch.skills} panel.
 * 绘制汉娜仅本人可见的浮游行（{@link HannaFloatHudRules}）。仅用于展示：读取本地玩家同步的真实职业、她的
 * {@link HannaComponent}（本地递减的浮游剩余刻，以及仅本人同步、依据客户端世界时钟倒数的就绪刻）与共享魔力，从不预测浮游；
 * 浮游是否生效只由服务端决定。仅在已确认的 SparkWitch 服务器上由 {@link HannaClient} 调用。属于职业自有展示：绝不使用
 * 动作栏，也绝不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class HannaFloatHud {
    // Render-thread cache: the Texts and widths are a pure function of the line, the ability key text, the active
    // Language (replaced on every resource reload), the font renderer and the font glyph options (`HudFontOptions`,
    // which a toggle changes without a reload), so they are rebuilt only when one changes.
    // 渲染线程缓存：文本与宽度只取决于该行、技能按键文本、当前 Language（每次资源重载都会换新）、字体渲染器与字形选项
    // （`HudFontOptions`，切换它们不会触发资源重载），因此只在其中之一变化时重建。
    private static HannaFloatHudRules.Line cachedLine;
    private static Text cachedKeyText;
    private static Language cachedLanguage;
    private static TextRenderer cachedRenderer;
    private static int cachedFontOptions = -1;
    private static Text cachedText;
    private static int cachedTextWidth;
    private static int cachedMeasured;

    private HannaFloatHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the real-role check. getRole stays raw, so a Black Raven
        // acting as Hanna never gets her line.
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回。getRole 始终是真实身份，扮演汉娜的黑羽鸦不会得到她的行。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean hanna = HannaRules.isHanna(game.getRole(player));
        if (!hanna) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!HannaFloatHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, hanna,
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        // Fail closed: no synced component, no line. / 失败即关闭：没有同步组件就不画这一行。
        HannaComponent state = HannaComponent.KEY.getNullable(player);
        if (state == null) {
            return;
        }
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        HannaFloatHudRules.Line line = HannaFloatHudRules.line(state.clientFloatTicks(),
                state.floatCooldownRemaining(client.world.getTime()), witch.hasManaSystem(), witch.getMana());
        // The corner row is taken by the shared witch skill line (defensive: Hanna has no witch skill id) or by the
        // Saint's Karma line, which any killer of a Hellfire Saint can carry, drawn flush in the corner.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：汉娜没有魔女技能 id），或被圣徒业报行占用（任何在地狱火
        // 期间击杀圣徒的人都可能带有，紧贴角落绘制）。
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        Text keyText = line.keyHint() ? SparkWitchClient.abilityKeyText() : null;
        Language language = Language.getInstance();
        int fontOptions = HudFontOptions.current();
        if (!line.equals(cachedLine) || !Objects.equals(keyText, cachedKeyText) || language != cachedLanguage
                || renderer != cachedRenderer
                || fontOptions != cachedFontOptions) {
            cachedText = text(line, line.value(), keyText);
            cachedTextWidth = renderer.getWidth(cachedText);
            cachedMeasured = renderer.getWidth(text(line, HannaFloatHudRules.measuredValue(line), keyText));
            cachedLine = line;
            cachedKeyText = keyText;
            cachedLanguage = language;
            cachedRenderer = renderer;
            cachedFontOptions = fontOptions;
        }
        int width = context.getScaledWindowWidth();
        int x = HannaFloatHudRules.rowX(width, cachedTextWidth);
        int y = HannaFloatHudRules.rowY(width, context.getScaledWindowHeight(), cachedMeasured, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, cachedText, x, y, line.color());
    }

    private static Text text(HannaFloatHudRules.Line line, int value, Text keyText) {
        return line.keyHint() ? Text.translatable(line.key(), keyText) : Text.translatable(line.key(), value);
    }
}
