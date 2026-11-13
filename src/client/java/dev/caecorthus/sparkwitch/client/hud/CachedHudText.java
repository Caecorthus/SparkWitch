package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.text.HudFontOptions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Render-thread cache of one role HUD row (Athlete, Noa, Hanna, and each of 橘雪莉's two rows): the Text and widths
 * are a pure function of the row's line, its key text, the active Language (replaced on every resource reload), the
 * font renderer and the font glyph options ({@link HudFontOptions}, which a toggle changes without a reload), so they
 * are rebuilt only when one changes. The row keeps its own role gate, line selection, colour and placement; it builds
 * the shown Text from {@code number} and the stable-width Text from {@code measuredNumber}.
 * 单个职业 HUD 行（运动员、诺亚、汉娜，以及橘雪莉两行中的每一行）的渲染线程缓存：文本与宽度只取决于该行、其按键文本、
 * 当前 Language（每次资源重载都会换新）、字体渲染器与字形选项（{@link HudFontOptions}，切换它们不会触发资源重载），
 * 因此只在其中之一变化时重建。职业判定、行的选择、颜色与位置仍由各行自己负责；显示文本由 {@code number} 构建，
 * 用于稳定宽度的文本由 {@code measuredNumber} 构建。
 *
 * @param <L> the row's immutable line record / 该行不可变的行记录
 */
public final class CachedHudText<L> {
    /** Builds a row's Text from its line, a number and the key text (null without a key hint). / 由行、数字与键名构建文本。 */
    @FunctionalInterface
    public interface Factory<L> {
        Text text(L line, int number, @Nullable Text keyText);
    }

    private final Factory<L> factory;
    private final ToIntFunction<L> number;
    private final ToIntFunction<L> measuredNumber;
    private L cachedLine;
    private Text cachedKeyText;
    private Language cachedLanguage;
    private TextRenderer cachedRenderer;
    private int cachedFontOptions = -1;
    private Text cachedText;
    private int cachedTextWidth;
    private int cachedMeasured;

    public CachedHudText(Factory<L> factory, ToIntFunction<L> number, ToIntFunction<L> measuredNumber) {
        this.factory = factory;
        this.number = number;
        this.measuredNumber = measuredNumber;
    }

    /** Rebuilds the Text and both widths when any cache key changed. / 任一缓存键变化时重建文本与两个宽度。 */
    public void update(L line, @Nullable Text keyText, TextRenderer renderer) {
        Language language = Language.getInstance();
        int fontOptions = HudFontOptions.current();
        if (!line.equals(cachedLine) || !Objects.equals(keyText, cachedKeyText) || language != cachedLanguage
                || renderer != cachedRenderer
                || fontOptions != cachedFontOptions) {
            cachedText = factory.text(line, number.applyAsInt(line), keyText);
            cachedTextWidth = renderer.getWidth(cachedText);
            cachedMeasured = renderer.getWidth(factory.text(line, measuredNumber.applyAsInt(line), keyText));
            cachedLine = line;
            cachedKeyText = keyText;
            cachedLanguage = language;
            cachedRenderer = renderer;
            cachedFontOptions = fontOptions;
        }
    }

    /** The shown Text. / 显示的文本。 */
    public Text text() {
        return cachedText;
    }

    /** Width of the shown Text. / 显示文本的宽度。 */
    public int width() {
        return cachedTextWidth;
    }

    /** Width of the stable-measure Text, so the row does not jitter as digits change. / 稳定测量文本的宽度。 */
    public int measuredWidth() {
        return cachedMeasured;
    }
}
