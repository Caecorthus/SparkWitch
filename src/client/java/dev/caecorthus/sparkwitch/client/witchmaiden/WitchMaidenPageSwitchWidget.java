package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Witch Maiden-owned avatar pagination button. / 巫女自有的头像翻页按钮。 */
public final class WitchMaidenPageSwitchWidget extends ButtonWidget {
    private final ItemStack icon;
    private final Text tooltip;

    public WitchMaidenPageSwitchWidget(
            int x,
            int y,
            ItemStack icon,
            Text tooltip,
            PressAction onPress
    ) {
        super(x, y, 16, 16, tooltip, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        this.tooltip = tooltip;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        context.drawItem(icon, getX(), getY());
        if (isHovered()) {
            int color = 0x90B04A8B;
            context.fillGradient(
                    RenderLayer.getGuiOverlay(),
                    getX(), getY(), getX() + 16, getY() + 16,
                    color, color, 0
            );
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltip, mouseX, mouseY);
        }
    }
}
