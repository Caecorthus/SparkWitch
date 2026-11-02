package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.text.Text;

/**
 * The "Mini Wither Appearance..." row that {@code MiniWitherSkinOptionsMixin} appends to vanilla Options → Skin
 * Customization: one full-width button (one {@code addWidgetEntry}) that opens {@link MiniWitherSkinScreen} with the
 * Skin Customization screen as its parent. It is not a {@code GameOptions} entry, so {@code options.txt} is untouched.
 * {@code MiniWitherSkinOptionsMixin} 追加到原版「选项 → 皮肤自定义」的「小凋灵外观...」一行：一个整行宽的按钮（一次
 * {@code addWidgetEntry}），打开以皮肤自定义界面为父界面的 {@link MiniWitherSkinScreen}。它不是 {@code GameOptions} 条目，
 * 因此不改动 {@code options.txt}。
 */
public final class MiniWitherSkinEntryButton {
    /** Vanilla's full option row width ({@code OptionListWidget#addSingleOptionEntry}). / 原版整行选项宽度。 */
    public static final int WIDTH = 310;

    private MiniWitherSkinEntryButton() {
    }

    public static void addTo(OptionListWidget body, Screen parent) {
        ButtonWidget button = ButtonWidget.builder(Text.translatable(MiniWitherSkinLangKeys.ENTRY),
                        ignored -> MinecraftClient.getInstance().setScreen(new MiniWitherSkinScreen(parent)))
                .width(WIDTH)
                .tooltip(Tooltip.of(Text.translatable(MiniWitherSkinLangKeys.ENTRY_TOOLTIP)))
                .build();
        body.addWidgetEntry(button, null);
    }
}
