package dev.caecorthus.sparkwitch.client.headset;

import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.ScreenTexts;

/**
 * The headset cell under the armor block on Wathe's inventory screen (BEAR WP4, E1), as an inactive widget like
 * {@code LimitedInventoryArmorPanel}: while visible, the info card and SparkAssist's guidebook route around it; being
 * inactive it never takes a press, so Wathe's own slot handling (extended by {@code LimitedInventoryArmorSlotsMixin})
 * receives every click. {@code visible} follows {@code LimitedInventoryArmorSlots.headsetShown} each frame. It paints
 * through the screen so the slot reuses Wathe's {@code drawSlot}.
 * Wathe 背包界面上护甲块下方的耳机格（BEAR WP4，E1），与 {@code LimitedInventoryArmorPanel} 一样是不可交互控件：可见时信息卡与
 * SparkAssist 指南会绕开它；它不可交互，从不吞掉按下，因此 Wathe 自己的栏位处理（由 {@code LimitedInventoryArmorSlotsMixin} 扩展）
 * 接收每次点击。{@code visible} 每帧跟随 {@code LimitedInventoryArmorSlots.headsetShown}。绘制交给界面完成，以复用 Wathe 的
 * {@code drawSlot}。
 */
public final class LimitedInventoryHeadsetCell extends ClickableWidget {
    @FunctionalInterface
    public interface Painter {
        void paint(DrawContext context, int mouseX, int mouseY);
    }

    private final Painter painter;

    /** {@code screenY} is the armor layout anchor. / {@code screenY} 为护甲布局锚点。 */
    public LimitedInventoryHeadsetCell(int screenX, int screenY, Painter painter) {
        super(LimitedInventoryArmorLayout.headsetPanelX(screenX), LimitedInventoryArmorLayout.headsetPanelY(screenY),
                LimitedInventoryArmorLayout.HEADSET_PANEL_WIDTH, LimitedInventoryArmorLayout.HEADSET_PANEL_HEIGHT,
                ScreenTexts.EMPTY);
        this.painter = painter;
        this.active = false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        painter.paint(context, mouseX, mouseY);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }
}
