package dev.caecorthus.sparkwitch.client.screen;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.hud.TarotDivinationHudRenderer;
import dev.caecorthus.sparkwitch.client.hud.TarotReadingSlipRenderer;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Entry;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Section;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingLog;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The divination selector's wording, per mode: prompts, the footer's question or hint, section and count labels and
 * tooltip lines. {@link TarotLedgerRenderer} measures and draws it.
 * 占卜选择界面按模式区分的文字：提示语、页脚问句或提示、分区与计数标签以及提示框各行。由 {@link TarotLedgerRenderer}
 * 测量并绘制。
 */
final class TarotLedgerText {
    private static final String ELLIPSIS = "…";
    private static final String AGE_SEPARATOR = " · ";

    private TarotLedgerText() {
    }

    static Text searchPrompt(boolean identity) {
        return Text.translatable(identity
                ? "screen.sparkwitch.tarot.search.identity"
                : "screen.sparkwitch.tarot.search.survival");
    }

    static Text hint(boolean identity) {
        return Text.translatable(identity
                ? "screen.sparkwitch.tarot.hint.identity"
                : "screen.sparkwitch.tarot.hint.survival");
    }

    static Text noMatch(boolean identity) {
        return Text.translatable(identity
                ? "screen.sparkwitch.tarot.none.identity"
                : "screen.sparkwitch.tarot.none.survival");
    }

    /** The paid question with its argument in TEXT_HI. 付费问句，参数以 TEXT_HI 显示。 */
    static MutableText question(boolean identity, String argument) {
        return Text.translatable(identity
                        ? "screen.sparkwitch.tarot.question.identity"
                        : "screen.sparkwitch.tarot.question.survival",
                Text.literal(argument).withColor(InventoryCardPaint.TEXT_HI & 0xFFFFFF));
    }

    /**
     * Shortens the question to {@code maxWidth}, the argument first so the question wording stays whole; only when
     * even "…" cannot replace it is the tail cut.
     * 将问句缩短到 {@code maxWidth}，优先截断参数以保留完整措辞；参数连 "…" 都放不下时才截断句尾。
     */
    static OrderedText fitQuestion(TextRenderer renderer, boolean identity, String argument, int maxWidth) {
        MutableText full = question(identity, argument);
        int width = renderer.getWidth(full);
        if (width <= maxWidth) {
            return full.asOrderedText();
        }
        int ellipsisWidth = renderer.getWidth(ELLIPSIS);
        int budget = maxWidth - (width - renderer.getWidth(argument));
        if (budget >= ellipsisWidth) {
            return question(identity, TarotDivinationHudRenderer.fit(renderer, argument, budget)).asOrderedText();
        }
        StringVisitable cut = renderer.trimToWidth(full, Math.max(0, maxWidth - ellipsisWidth));
        return Language.getInstance().reorder(StringVisitable.concat(cut, StringVisitable.plain(ELLIPSIS)));
    }

    static MutableText count(boolean identity, int count) {
        String key = identity
                ? count == 1 ? "screen.sparkwitch.tarot.count.role" : "screen.sparkwitch.tarot.count.roles"
                : count == 1 ? "screen.sparkwitch.tarot.count.player" : "screen.sparkwitch.tarot.count.players";
        return Text.translatable(key, count);
    }

    static Text sectionLabel(Section section) {
        return Text.translatable(switch (section) {
            case CIVILIAN -> "hud.sparkwitch.tarot.faction.civilian";
            case KILLER -> "hud.sparkwitch.tarot.faction.killer";
            case NEUTRAL -> "hud.sparkwitch.tarot.faction.neutral";
            case WITCH -> "hud.sparkwitch.tarot.faction.witch";
            case SPECIAL -> "screen.sparkwitch.tarot.section.special";
            case PLAYERS -> "screen.sparkwitch.tarot.section.players";
        });
    }

    /**
     * A stamped row repeats its verdict and age; otherwise a cut name shows in full. "Click again" is added only on
     * an armed selection, the one a click on it would now submit.
     * 带印记的行重复其结论与时间；否则被截断的名称显示全名。仅在已确认的选中项（此时单击即会提交）上追加
     * “再次单击即可占卜”。
     */
    static List<Text> cellTooltip(Entry entry, @Nullable TarotReadingLog.Reading reading, boolean nameCut,
                                  boolean armedSelection) {
        List<Text> lines = new ArrayList<>(3);
        if (reading != null) {
            lines.add(TarotReadingSlipRenderer.sentence(reading, InventoryCardPaint.TEXT_HI & 0xFFFFFF));
            lines.add(age(reading));
        } else if (nameCut) {
            lines.add(Text.literal(entry.label()));
        }
        if (armedSelection) {
            lines.add(Text.translatable("screen.sparkwitch.tarot.again"));
        }
        return lines;
    }

    /** Survival readings go stale, so they say they hold as of the reading. 存活结果会过时，因此注明以占卜时为准。 */
    private static Text age(TarotReadingLog.Reading reading) {
        int minutes = TarotReadingLog.minutesAgo(Util.getMeasuringTimeMs(), reading.receivedAtMs());
        MutableText age = minutes <= 0
                ? Text.translatable("hud.sparkwitch.tarot.reading.ago_now")
                : Text.translatable("hud.sparkwitch.tarot.reading.ago", minutes);
        if (reading.mode() == OpenTarotDivinationSelectorS2CPacket.MODE_SURVIVAL) {
            age.append(AGE_SEPARATOR).append(Text.translatable("hud.sparkwitch.tarot.reading.snapshot"));
        }
        return age;
    }
}
