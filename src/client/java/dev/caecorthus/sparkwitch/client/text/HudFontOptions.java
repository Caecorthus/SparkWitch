package dev.caecorthus.sparkwitch.client.text;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * The font options that change glyph widths without a resource reload: Force Unicode Font and Japanese Glyph Variants
 * are the only inputs of vanilla {@code FontManager.getActiveFilters}, and toggling either swaps the active glyph filters
 * while keeping the same {@code Language} and {@code TextRenderer}. HUD caches that store measured widths add this to
 * their key so a toggle re-measures on the next frame.
 * 无需资源重载即可改变字形宽度的字体选项：强制 Unicode 字体与日文字形变体是原版 {@code FontManager.getActiveFilters} 的
 * 全部输入，切换任一项都会更换字形过滤器，而 {@code Language} 与 {@code TextRenderer} 保持不变。缓存了测量宽度的 HUD
 * 把它加入缓存键，切换后下一帧即重新测量。
 */
public final class HudFontOptions {
    private HudFontOptions() {
    }

    /** Bit 0: Force Unicode Font; bit 1: Japanese Glyph Variants. / 位 0：强制 Unicode 字体；位 1：日文字形变体。 */
    public static int current() {
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) {
            return 0;
        }
        int bits = 0;
        if (Boolean.TRUE.equals(options.getForceUnicodeFont().getValue())) {
            bits |= 1;
        }
        if (Boolean.TRUE.equals(options.getJapaneseGlyphVariants().getValue())) {
            bits |= 2;
        }
        return bits;
    }
}
