package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumBlessing;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumWindowKind;
import dev.caecorthus.sparkwitch.util.TickTime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dead's 幽灵的加护 vote screen (owner pick 2026-11-13: 2x2 cards on the 通灵 vote screen's Harpy Express panel):
 * four blessing cards with icon, name and effect, the countdown, and the footer with the remembered pick. A click on an
 * offered card sends that blessing and closes; a card the server disabled for this window (SANITY for a caster without
 * real sanity, i.e. a SparkTraits Impostor) is a sunken slot whose effect line names the reason, and ignores the mouse.
 * Esc closes without voting; the shared ability key re-opens the screen to vote or change the vote while the window is
 * live. Every other key, number keys included, is swallowed: the screen pops up by itself while a spectator may be
 * pressing 1-9 for vanilla's spectator menu, so no key may ever cast a vote. The screen belongs to one window and
 * closes by itself when that window ends, a new one starts, or the player may no longer vote. The server re-checks
 * every vote.
 * 死者的幽灵的加护投票界面（所有者 2026-11-13 选定：在通灵投票界面的哈比特快面板上排列 2x2 卡片）：四张带图标、名称与效果
 * 的加护卡片、倒计时，以及显示已记住选择的页脚。点击可选的卡片即发送该加护并关闭；服务端在本窗口禁用的卡片（施放者没有真实
 * 理智，即带 SparkTraits 内鬼特质时的回满理智）显示为凹槽，效果行写明原因，且不响应鼠标。Esc 关闭但不投票；窗口进行期间按共享
 * 技能键可重新打开以投票或改票。其余按键（包括数字键）一律被吞掉：界面会自行弹出，此时旁观者可能正按着原版旁观菜单的 1-9，
 * 因此任何按键都不能投票。界面只属于一个窗口：该窗口结束、新窗口开始或玩家不再可投票时自动关闭。每一票都由服务端重新校验。
 */
public final class MediumBlessingVoteScreen extends Screen {
    private static final int ICON_GAP = 3;
    private static final int HINT_GAP = 5;
    /** The countdown turns red for the last seconds. / 最后几秒倒计时变红。 */
    private static final int URGENT_SECONDS = 5;
    private static final String ESC_LABEL = "Esc";
    private static final String REASON_KEY = "screen.sparkwitch.medium.blessing.locked.impostor";

    private final int windowId;

    // Rebuilt by init() (resize, GUI scale, language). / 由 init() 重建（窗口尺寸、界面缩放、语言变化）。
    private MediumBlessingVoteLayout.Layout layout = MediumBlessingVoteLayout.layout(0, 0, 1);
    private final List<List<String>> effectLines = new ArrayList<>();
    private String reasonLine = "";

    MediumBlessingVoteScreen(int windowId) {
        super(Text.translatable("screen.sparkwitch.medium.blessing.title"));
        this.windowId = windowId;
    }

    /** Lang key of a blessing's card name. / 加护卡片名称的语言键。 */
    static String nameKey(MediumBlessing blessing) {
        return "screen.sparkwitch.medium.blessing." + blessing.name().toLowerCase(Locale.ROOT);
    }

    /** Lang key of a blessing's one-line effect. / 加护效果说明的语言键。 */
    static String effectKey(MediumBlessing blessing) {
        return nameKey(blessing) + ".effect";
    }

    @Override
    protected void init() {
        // Measure at one line first: the card width does not depend on the card height.
        // 先按一行测量：卡片宽度与卡片高度无关。
        int budget = MediumBlessingVoteLayout.layout(this.width, this.height, 1).effectWidth();
        effectLines.clear();
        int lines = 1;
        for (MediumBlessing blessing : MediumBlessing.values()) {
            List<String> wrapped = wrap(Text.translatable(effectKey(blessing)).getString(), budget);
            effectLines.add(wrapped);
            lines = Math.max(lines, wrapped.size());
        }
        reasonLine = InventoryCardPaint.ellipsize(this.textRenderer, Text.translatable(REASON_KEY).getString(), budget);
        layout = MediumBlessingVoteLayout.layout(this.width, this.height, lines);
    }

    /**
     * The effect in at most {@link MediumBlessingVoteLayout#MAX_EFFECT_LINES} lines; only text too long even for that
     * loses its tail to an ellipsis.
     * 效果说明至多 {@link MediumBlessingVoteLayout#MAX_EFFECT_LINES} 行；只有仍放不下的文字才以省略号截断结尾。
     */
    private List<String> wrap(String text, int budget) {
        List<StringVisitable> wrapped = this.textRenderer.getTextHandler().wrapLines(text, Math.max(1, budget),
                Style.EMPTY);
        int max = MediumBlessingVoteLayout.MAX_EFFECT_LINES;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < wrapped.size() && lines.size() < max; i++) {
            if (lines.size() == max - 1 && i < wrapped.size() - 1) {
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < wrapped.size(); j++) {
                    rest.append(wrapped.get(j).getString());
                }
                lines.add(InventoryCardPaint.ellipsize(this.textRenderer, rest.toString(), budget));
            } else {
                lines.add(InventoryCardPaint.ellipsize(this.textRenderer, wrapped.get(i).getString(), budget));
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Closes once this window can no longer take the local player's vote: it ended, a newer one replaced it, it is not
     * a blessing window, or the player left the voter gate.
     * 本窗口无法再接受本地玩家的投票时关闭：窗口结束、被更新的窗口取代、不是加护窗口，或玩家不再满足投票门槛。
     */
    @Override
    public void tick() {
        if (this.client == null
                || !MediumVoteClient.canVote(this.client)
                || MediumVoteClient.kind(this.client) != MediumWindowKind.BLESSING
                || MediumSeanceComponent.KEY.get(this.client.world).getWindowId() != windowId) {
            close();
        }
    }

    /**
     * Scrim only, without vanilla's blur: the dead keep spectating behind the panel.
     * 只画遮罩，不使用原版模糊：死者仍可透过面板继续观战。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, TarotLedgerPaint.LEDGER_SCRIM);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MediumBlessingVoteLayout.Rect panel = layout.panel();
        int px = panel.x();
        int py = panel.y();
        int pw = panel.width();
        Integer choice = MediumVoteClient.choice(windowId);
        int hovered = layout.cardAt(mouseX, mouseY);
        MediumBlessing[] blessings = MediumBlessing.values();

        int seconds = secondsLeft();
        Text countdown = Text.translatable("hud.sparkwitch.medium.seance.remaining", seconds);
        int countdownColor = seconds <= URGENT_SECONDS ? InventoryCardPaint.ALERT_TEXT : InventoryCardPaint.MUTED;
        int countdownWidth = 5 + ICON_GAP + this.textRenderer.getWidth(countdown);
        int countdownX = px + (pw - countdownWidth) / 2;
        int keycapX = px + TarotLedgerLayout.FOOTER_INSET;
        int keycapY = layout.footerY() + TarotLedgerLayout.KEYCAP_Y;
        int keycapWidth = TarotLedgerLayout.keycapWidth(this.textRenderer.getWidth(ESC_LABEL));

        InventoryCardPaint.panel(context, px, py, pw, panel.height());
        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            TarotLedgerPaint.titleBand(context, px, py, pw);
            MediumPaint.wisp(context, px + TarotLedgerLayout.TITLE_ICON_X, py + TarotLedgerLayout.TITLE_Y,
                    MediumPaint.TEAL);
            InventoryCardPaint.hourglass(context, countdownX, layout.countdownY() + 1, countdownColor);
            for (int i = 0; i < blessings.length && i < layout.cards().size(); i++) {
                MediumBlessingVoteLayout.Rect card = layout.cards().get(i);
                boolean locked = isLocked(blessings[i]);
                boolean chosen = !locked && choice != null && choice == i;
                boolean hot = !locked && hovered == i;
                if (locked) {
                    MediumPaint.lockedCard(context, card.x(), card.y(), card.width(), card.height());
                } else {
                    MediumPaint.voteButton(context, card.x(), card.y(), card.width(), card.height(), hot, chosen);
                }
                MediumPaint.blessingIcon(context, card.x() + MediumBlessingVoteLayout.ICON_X,
                        card.y() + MediumBlessingVoteLayout.ICON_Y, blessings[i], iconColor(locked, hot, chosen));
                int markX = card.right() - MediumBlessingVoteLayout.MARK_RIGHT;
                if (chosen) {
                    TarotLedgerPaint.stamp(context, markX, card.y() + MediumBlessingVoteLayout.NAME_Y, true,
                            MediumPaint.TEAL);
                } else if (locked) {
                    InventoryCardPaint.padlock(context, markX, card.y() + MediumBlessingVoteLayout.NAME_Y - 1,
                            InventoryCardPaint.MUTED, InventoryCardPaint.WELL);
                }
            }
            TarotLedgerPaint.etched(context, px + TarotLedgerLayout.FOOTER_INSET,
                    px + pw - TarotLedgerLayout.FOOTER_INSET, layout.ruleY());
            TarotLedgerPaint.keycap(context, keycapX, keycapY, keycapWidth);
        });

        context.drawText(this.textRenderer,
                InventoryCardPaint.ellipsize(this.textRenderer, this.title.getString(),
                        pw - TarotLedgerLayout.TITLE_X - MediumBlessingVoteLayout.INSET),
                px + TarotLedgerLayout.TITLE_X, py + TarotLedgerLayout.TITLE_Y, InventoryCardPaint.TITLE, false);
        String asks = InventoryCardPaint.ellipsize(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.blessing.asks").getString(),
                pw - 2 * MediumBlessingVoteLayout.INSET);
        context.drawText(this.textRenderer, asks, px + (pw - this.textRenderer.getWidth(asks)) / 2,
                py + MediumBlessingVoteLayout.ASKS_Y, InventoryCardPaint.MUTED, true);
        context.drawText(this.textRenderer, countdown, countdownX + 5 + ICON_GAP, layout.countdownY(), countdownColor,
                true);

        for (int i = 0; i < blessings.length && i < layout.cards().size(); i++) {
            drawCardText(context, layout.cards().get(i), i, blessings[i], hovered == i, choice);
        }
        drawFooter(context, choice, keycapX, keycapY, keycapWidth);
    }

    private void drawCardText(DrawContext context, MediumBlessingVoteLayout.Rect card, int index,
                              MediumBlessing blessing, boolean hovered, @Nullable Integer choice) {
        boolean locked = isLocked(blessing);
        boolean chosen = !locked && choice != null && choice == index;
        boolean hot = !locked && hovered;
        int nameColor = locked ? InventoryCardPaint.FAINT : MediumPaint.voteLabelColor(hot, chosen);
        String name = InventoryCardPaint.ellipsize(this.textRenderer,
                Text.translatable(nameKey(blessing)).getString(), layout.nameWidth());
        context.drawText(this.textRenderer, name, card.x() + MediumBlessingVoteLayout.NAME_X,
                card.y() + MediumBlessingVoteLayout.NAME_Y, nameColor, false);
        int x = card.x() + MediumBlessingVoteLayout.EFFECT_X;
        int y = card.y() + MediumBlessingVoteLayout.EFFECT_Y;
        if (locked) {
            // The owner rule (2026-11-13): the dead see why the option is greyed out. / 所有者规则：死者能看到置灰的原因。
            context.drawText(this.textRenderer, reasonLine, x, y, InventoryCardPaint.ALERT_TEXT, true);
            return;
        }
        List<String> lines = index < effectLines.size() ? effectLines.get(index) : List.of();
        for (int i = 0; i < lines.size(); i++) {
            context.drawText(this.textRenderer, lines.get(i), x, y + i * MediumBlessingVoteLayout.LINE_HEIGHT,
                    InventoryCardPaint.TIP_DESC, true);
        }
    }

    /**
     * Esc keycap and the "choose later" hint on the left, the pick already sent on the right in teal. When both do
     * not fit, the hint gives way.
     * 左侧为 Esc 键帽与「稍后再选」提示，右侧以青绿显示已提交的选择；两者放不下时省略提示。
     */
    private void drawFooter(DrawContext context, @Nullable Integer choice, int keycapX, int keycapY, int keycapWidth) {
        MediumBlessingVoteLayout.Rect panel = layout.panel();
        int textY = layout.footerY() + TarotLedgerLayout.FOOTER_TEXT_Y;
        context.drawText(this.textRenderer, ESC_LABEL, keycapX + TarotLedgerLayout.KEYCAP_LABEL_X,
                keycapY + TarotLedgerLayout.KEYCAP_LABEL_Y, InventoryCardPaint.FAINT, false);
        int right = panel.right() - TarotLedgerLayout.FOOTER_INSET;
        int currentX = right;
        MediumBlessing picked = choice == null ? null : MediumBlessing.byIndex(choice);
        if (picked != null) {
            Text current = Text.translatable("screen.sparkwitch.medium.blessing.current",
                    Text.translatable(nameKey(picked)).withColor(MediumPaint.TEAL & 0xFFFFFF));
            currentX = right - this.textRenderer.getWidth(current);
            context.drawText(this.textRenderer, current, currentX, textY, InventoryCardPaint.TEXT, true);
        }
        Text later = Text.translatable("screen.sparkwitch.medium.blessing.later", SparkWitchClient.abilityKeyText());
        int laterX = keycapX + keycapWidth + HINT_GAP;
        if (laterX + this.textRenderer.getWidth(later) + HINT_GAP <= currentX) {
            context.drawText(this.textRenderer, later, laterX, textY, InventoryCardPaint.MUTED, true);
        }
    }

    private static int iconColor(boolean locked, boolean hovered, boolean chosen) {
        if (locked) {
            return InventoryCardPaint.BRASS_LO;
        }
        if (chosen) {
            return MediumPaint.TEAL;
        }
        return hovered ? InventoryCardPaint.COIN : InventoryCardPaint.BRASS_HI;
    }

    private boolean isLocked(MediumBlessing blessing) {
        return this.client != null && this.client.world != null
                && MediumSeanceComponent.KEY.get(this.client.world).isDisabled(blessing);
    }

    private int secondsLeft() {
        return this.client == null || this.client.world == null
                ? 0
                : TickTime.ceilSeconds(MediumSeanceComponent.KEY.get(this.client.world).getTicksLeft());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int index = layout.cardAt(mouseX, mouseY);
        MediumBlessing blessing = MediumBlessing.byIndex(index);
        if (blessing == null || isLocked(blessing)) {
            // A click on a gap, a locked card or anywhere outside neither votes nor closes.
            // 点击间隔、锁定卡片或面板外既不投票也不关闭。
            return false;
        }
        MediumVoteClient.submit(windowId, blessing.index());
        close();
        return true;
    }

    /**
     * Only Esc acts. Every other key (Enter, Space, Tab and the number keys included) is swallowed, so no key can ever
     * cast a vote by accident.
     * 只有 Esc 生效。其余按键（包括回车、空格、Tab 与数字键）一律被吞掉，任何按键都不会误投。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
