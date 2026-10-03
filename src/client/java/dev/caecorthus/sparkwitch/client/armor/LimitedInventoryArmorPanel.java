package dev.caecorthus.sparkwitch.client.armor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.ScreenTexts;

/**
 * The armor block as a visible but inactive widget on Wathe's inventory screen. Other overlays on that screen (the
 * SparkWitch / SparkTraits info card, SparkAssist's guidebook) treat every visible {@code ClickableWidget} child as an
 * obstacle and route around it; being inactive, it never consumes a press, so Wathe's own slot handling (extended by
 * {@code LimitedInventoryArmorSlotsMixin}) still receives every click and drag. It paints through the screen so the
 * slots reuse Wathe's {@code drawSlot}.
 * 护甲块在 Wathe 背包上是可见但不可交互的控件。该界面上的其他覆盖层（SparkWitch / SparkTraits 信息卡、SparkAssist
 * 指南）把所有可见的 {@code ClickableWidget} 子元素视为障碍并绕开；它不可交互，因此从不吞掉点击，Wathe 自己的槽位
 * 处理（由 {@code LimitedInventoryArmorSlotsMixin} 扩展）仍接收每次点击与拖动。绘制交给界面完成，以复用 Wathe 的
 * {@code drawSlot}。
 */
public final class LimitedInventoryArmorPanel extends ClickableWidget {
    @FunctionalInterface
    public interface Painter {
        void paint(DrawContext context, int mouseX, int mouseY);
    }

    private final Painter painter;

    public LimitedInventoryArmorPanel(int screenX, int screenY, Painter painter) {
        super(LimitedInventoryArmorLayout.panelX(screenX), LimitedInventoryArmorLayout.panelY(screenY),
                LimitedInventoryArmorLayout.PANEL_WIDTH, LimitedInventoryArmorLayout.PANEL_HEIGHT, ScreenTexts.EMPTY);
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
