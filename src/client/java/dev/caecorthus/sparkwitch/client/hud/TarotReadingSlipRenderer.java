package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingLog;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingSlipLayout;
import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Shows the Tarot Reader's newest reading as a brass slip above the actionbar, where actionbar countdowns cannot
 * overwrite it. The verdict comes only from the server's purchaser-only reading payload; the client derives nothing.
 * Role-owned presentation, never part of the witch skill inventory panel.
 * 以黄铜结果条在动作栏上方显示塔罗牌师最新的占卜结果，动作栏倒计时无法覆盖它。结论只来自服务端仅发给购买者的
 * 结果数据包，客户端不做任何推断。属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class TarotReadingSlipRenderer {
    private static final String IDENTITY_PRESENT_KEY = "message.sparkwitch.tarot.identity.present";
    private static final String IDENTITY_ABSENT_KEY = "message.sparkwitch.tarot.identity.absent";
    private static final String SURVIVAL_ALIVE_KEY = "message.sparkwitch.tarot.survival.alive";
    private static final String SURVIVAL_DEAD_KEY = "message.sparkwitch.tarot.survival.dead";
    private static final int ARGUMENT_RGB = InventoryCardPaint.TEXT_HI & 0xFFFFFF;

    private TarotReadingSlipRenderer() {
    }

    public static void render(DrawContext context) {
        TarotReadingLog.Reading reading = TarotDivinationClientState.readingLog().newest().orElse(null);
        if (reading == null) {
            return;
        }
        int alpha = TarotReadingSlipLayout.alpha(Util.getMeasuringTimeMs() - reading.receivedAtMs());
        if (alpha == 0) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        MutableText text = fitted(renderer, reading, screenWidth);
        TarotReadingSlipLayout.Geometry g = TarotReadingSlipLayout.geometry(
                screenWidth, context.getScaledWindowHeight(), renderer.getWidth(text));

        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            frame(context, g.x(), g.y(), g.width(), g.height(), alpha);
            TarotDivinationHudRenderer.drawIcon(context, g.iconX(), g.iconY(), alpha);
            int stamp = TarotLedgerPaint.stampColor(reading.mode(), reading.positive());
            TarotLedgerPaint.stamp(context, g.stampX(), g.stampY(), reading.positive(),
                    TarotReadingSlipLayout.withAlpha(stamp, alpha));
        });
        context.drawTextWithShadow(renderer, text, g.textX(), g.textY(),
                TarotReadingSlipLayout.withAlpha(InventoryCardPaint.TEXT, alpha));
    }

    /**
     * The verdict sentence from the four {@code message.sparkwitch.tarot.*} keys, with the argument (role name or
     * player name) in {@code argumentRgb}; the rest takes the caller's draw colour.
     * 由四个 {@code message.sparkwitch.tarot.*} 键生成的结论句，参数（职业名或玩家名）使用 {@code argumentRgb}，
     * 其余文字使用调用方的绘制颜色。
     */
    public static MutableText sentence(TarotReadingLog.Reading reading, int argumentRgb) {
        return Text.translatable(verdictKey(reading), argument(reading).withColor(argumentRgb & 0xFFFFFF));
    }

    /**
     * The sentence clamped to the screen: only the argument is ellipsized, never the verdict around it.
     * 按屏幕宽度收窄的句子：只截断参数，从不截断其两侧的结论文字。
     */
    private static MutableText fitted(TextRenderer renderer, TarotReadingLog.Reading reading, int screenWidth) {
        MutableText text = sentence(reading, ARGUMENT_RGB);
        int width = renderer.getWidth(text);
        if (width <= TarotReadingSlipLayout.textBudget(screenWidth)) {
            return text;
        }
        String argument = argument(reading).getString();
        int verdictWidth = width - renderer.getWidth(argument);
        String shown = TarotDivinationHudRenderer.fit(renderer, argument,
                TarotReadingSlipLayout.argumentBudget(screenWidth, verdictWidth));
        return Text.translatable(verdictKey(reading), Text.literal(shown).withColor(ARGUMENT_RGB));
    }

    private static String verdictKey(TarotReadingLog.Reading reading) {
        if (identity(reading)) {
            return reading.positive() ? IDENTITY_PRESENT_KEY : IDENTITY_ABSENT_KEY;
        }
        return reading.positive() ? SURVIVAL_ALIVE_KEY : SURVIVAL_DEAD_KEY;
    }

    /**
     * Identity: the role name localized from the target id, as the server words it; survival: the server-resolved
     * player name. Either falls back to the raw target.
     * 身份占卜：按目标 id 本地化职业名，与服务端措辞一致；存活占卜：服务端解析的玩家名。两者均以原始目标兜底。
     */
    private static MutableText argument(TarotReadingLog.Reading reading) {
        if (identity(reading)) {
            Identifier roleId = Identifier.tryParse(reading.target());
            Role role = roleId == null ? null : WatheRoles.getRole(roleId);
            return role == null
                    ? Text.literal(reading.target())
                    : WitchRoleDisplayTexts.roleName(RoleDisplayTextRules.roleTranslationKey(role));
        }
        return Text.literal(reading.displayName().isEmpty() ? reading.target() : reading.displayName());
    }

    private static boolean identity(TarotReadingLog.Reading reading) {
        return reading.mode() == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY;
    }

    /**
     * A copy of {@code InventoryCardPaint.brassTooltip}'s pixels, kept here on purpose: brassTooltip takes no alpha
     * and paints its brass ring over its own backing. The slip fades out, so every colour is scaled by {@code alpha}
     * and no two fills overlap, which blends each pixel exactly once. At alpha 0xFF this matches brassTooltip pixel
     * for pixel.
     * 有意复制 {@code InventoryCardPaint.brassTooltip} 的像素：brassTooltip 不接受透明度，且把黄铜边叠画在自身底色上。
     * 结果条需要渐隐，因此每种颜色都按 {@code alpha} 缩放，且各填充互不重叠，每个像素只混合一次；alpha 为 0xFF 时与
     * brassTooltip 逐像素一致。
     */
    private static void frame(DrawContext c, int x, int y, int w, int h, int alpha) {
        int back = TarotReadingSlipLayout.withAlpha(InventoryCardPaint.TIP_BG, alpha);
        int top = TarotReadingSlipLayout.withAlpha(InventoryCardPaint.BRASS_HI, alpha);
        int bottom = TarotReadingSlipLayout.withAlpha(InventoryCardPaint.BRASS_LO, alpha);
        c.fill(x - 3, y - 4, x + w + 3, y - 3, back);
        c.fill(x - 3, y + h + 3, x + w + 3, y + h + 4, back);
        c.fill(x - 4, y - 3, x - 3, y + h + 3, back);
        c.fill(x + w + 3, y - 3, x + w + 4, y + h + 3, back);
        c.fill(x - 3, y - 3, x + w + 3, y - 2, top);
        c.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, bottom);
        c.fillGradient(x - 3, y - 2, x - 2, y + h + 2, top, bottom);
        c.fillGradient(x + w + 2, y - 2, x + w + 3, y + h + 2, top, bottom);
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, back);
    }
}
