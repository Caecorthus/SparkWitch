package dev.caecorthus.sparkwitch.client.natsume;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 夏目安安 hypnosis HUD garble (owner 2026-11-03: "only the HUD is garbled; item names and tooltips are left alone; the
 * two hypnosis lines stay readable"). {@code mixin/natsume/NatsumeHudGarbleScopeMixin} opens the scope around
 * {@code InGameHud.render} and suspends it for the chat HUD and the held-item name popup;
 * {@code mixin/natsume/NatsumeGarbleMixin} swaps the {@code Text} and {@code String} arguments of
 * {@code DrawContext.drawText} and suspends the scope for item slot decorations. Nothing is garbled while a screen is
 * open, so screens, container titles and tooltips are never touched. Natsume's own lines are exempt by translation key.
 * The garble is stable: it depends only on the string and a {@value #BUCKET_TICKS}-tick world-time bucket, keeps every
 * style (colour included), whitespace and legacy {@code §} codes, and swaps each glyph for one of the same width class.
 * Render thread only; display only.
 * 夏目安安催眠的 HUD 乱码（所有者 2026-11-03：“只乱 HUD，物品名与 tooltip 不动，两条催眠提示保持可读”）。
 * {@code mixin/natsume/NatsumeHudGarbleScopeMixin} 在 {@code InGameHud.render} 外围开启作用域，并在聊天 HUD 与手持物品名
 * 弹出时暂停；{@code mixin/natsume/NatsumeGarbleMixin} 替换 {@code DrawContext.drawText} 的 {@code Text} 与 {@code String}
 * 参数，并在物品格装饰（数量）期间暂停。打开任何界面时都不乱码，因此界面、容器标题与 tooltip 永不受影响。夏目安安自己的
 * 提示按翻译键豁免。乱码是稳定的：只取决于字符串与 {@value #BUCKET_TICKS} 刻的世界时间分段，保留全部样式（含颜色）、空白与
 * 旧式 {@code §} 代码，每个字形替换为同宽度类别的字形。仅渲染线程；仅展示。
 */
public final class NatsumeHudGarble {
    /** The garble re-rolls once per this many world ticks. / 乱码每隔这么多世界刻重新生成一次。 */
    public static final int BUCKET_TICKS = 5;
    /** Translation-key prefixes of Natsume's own lines, which stay readable. / 夏目安安自身提示的翻译键前缀，保持可读。 */
    static final String MESSAGE_PREFIX = "message.sparkwitch.natsume.";
    static final String HUD_PREFIX = "hud.sparkwitch.natsume.";
    private static final int MAX_EXEMPT_DEPTH = 8;
    // Same-width replacements in the vanilla font: 5-px ASCII glyphs and dense CJK ideographs (no real words), so a
    // centred or right-aligned line keeps its footprint. / 原版字体中的同宽替换：5 像素 ASCII 字形与笔画繁密的汉字
    // （不成词），居中或右对齐的行保持原占位。
    private static final String NARROW_POOL = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdeghjmnopqrsuvwxyz0123456789#$%&?";
    private static final String WIDE_POOL = "龘靐齉爩鱻麤龗灪籱厵鸞驫纞鬱爨癵醻嚻矗靇龖贔羴雥灥麣";

    private static int hudDepth;
    private static int suspendDepth;

    private NatsumeHudGarble() {
    }

    /** {@code InGameHud.render} entered / left. / 进入、离开 {@code InGameHud.render}。 */
    public static void enterHud() {
        hudDepth++;
    }

    public static void exitHud() {
        hudDepth = Math.max(0, hudDepth - 1);
    }

    /** A part of the HUD pass that is never garbled (chat, held-item name, item slot decorations). / 永不乱码的部分。 */
    public static void suspend() {
        suspendDepth++;
    }

    public static void resume() {
        suspendDepth = Math.max(0, suspendDepth - 1);
    }

    /** Inside the HUD pass and not in a suspended part. / 处于 HUD 绘制中且不在暂停部分内。 */
    static boolean inHudPass() {
        return hudDepth > 0 && suspendDepth == 0;
    }

    /** Draw-time entry for {@code drawText(…, Text, …)}; returns the argument unchanged unless garbling applies. */
    public static @Nullable Text garbleHud(@Nullable Text text) {
        // Cheapest first: every text draw in the game, screens included, reaches this. / 先做最便宜的判断。
        if (text == null || !inHudPass()) {
            return text;
        }
        long bucket = activeBucket();
        if (bucket < 0L || isExempt(text)) {
            return text;
        }
        return garble(text, bucket);
    }

    /** Draw-time entry for {@code drawText(…, String, …)}; strings carry no key, so none is exempt. */
    public static @Nullable String garbleHud(@Nullable String text) {
        if (text == null || text.isEmpty() || !inHudPass()) {
            return text;
        }
        long bucket = activeBucket();
        return bucket < 0L ? text : garble(text, bucket);
    }

    /** The current time bucket while garbling applies, else -1: no screen open and the local player bound. */
    private static long activeBucket() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.world == null || !NatsumeClientState.bindsLocalPlayer()) {
            return -1L;
        }
        return Math.floorDiv(client.world.getTime(), BUCKET_TICKS) & Long.MAX_VALUE;
    }

    /**
     * Pure: whether a key in the text tree (content, translation arguments, siblings) is one of Natsume's own lines.
     * Matched by key, never by rendered words, so it holds in every language.
     * 纯函数：文字树（内容、翻译参数、子节点）中是否有夏目安安自身提示的翻译键。按键匹配而非按显示文字，任何语言都成立。
     */
    public static boolean isExempt(Text text) {
        return isExempt(text, 0);
    }

    private static boolean isExempt(Text text, int depth) {
        if (depth > MAX_EXEMPT_DEPTH) {
            return false;
        }
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            if (isNatsumeKey(translatable.getKey())) {
                return true;
            }
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Text nested && isExempt(nested, depth + 1)) {
                    return true;
                }
            }
        }
        for (Text sibling : text.getSiblings()) {
            if (isExempt(sibling, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    static boolean isNatsumeKey(String key) {
        return key.startsWith(MESSAGE_PREFIX) || key.startsWith(HUD_PREFIX);
    }

    /**
     * Pure: the garbled copy of a styled text, one literal per styled segment with that segment's style, seeded by
     * the whole string and the bucket.
     * 纯函数：带样式文字的乱码副本，每个样式片段一个保留原样式的字面量，种子取整串文字与时间分段。
     */
    public static Text garble(Text text, long bucket) {
        String whole = text.getString();
        if (whole.isEmpty()) {
            return text;
        }
        Mixer mixer = new Mixer(seed(whole, bucket));
        MutableText garbled = Text.empty();
        text.visit((style, segment) -> {
            garbled.append(Text.literal(garble(segment, mixer)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return garbled;
    }

    /** Pure: the garbled copy of a plain string. / 纯函数：普通字符串的乱码副本。 */
    public static String garble(String raw, long bucket) {
        return raw.isEmpty() ? raw : garble(raw, new Mixer(seed(raw, bucket)));
    }

    private static String garble(String raw, Mixer mixer) {
        StringBuilder out = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            int codePoint = raw.codePointAt(index);
            int length = Character.charCount(codePoint);
            if (codePoint == '§' && index + 1 < raw.length()) {
                // Legacy formatting code: keep the marker and its code character. / 旧式格式代码：保留标记与代码字符。
                out.append(raw, index, index + 2);
                index += 2;
                continue;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                out.appendCodePoint(codePoint);
            } else {
                String pool = isWide(codePoint) ? WIDE_POOL : NARROW_POOL;
                out.append(pool.charAt(mixer.next(pool.length())));
            }
            index += length;
        }
        return out.toString();
    }

    /** Full-width scripts and forms, drawn by the wide unifont glyphs. / 全角文字与字形，使用较宽的 unifont 字形绘制。 */
    static boolean isWide(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || codePoint >= 0x3000 && codePoint <= 0x303F
                || codePoint >= 0xFF01 && codePoint <= 0xFF60;
    }

    private static long seed(String whole, long bucket) {
        return whole.hashCode() * 0x9E3779B97F4A7C15L + bucket * 0xC2B2AE3D27D4EB4FL;
    }

    /** SplitMix64: allocation-light and deterministic per seed. / SplitMix64：分配少且同种子结果确定。 */
    private static final class Mixer {
        private long state;

        private Mixer(long seed) {
            this.state = seed;
        }

        int next(int bound) {
            state += 0x9E3779B97F4A7C15L;
            long z = state;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            return (int) Math.floorMod(z, (long) bound);
        }
    }
}
