package dev.caecorthus.sparkwitch.client.magician;

import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import dev.caecorthus.sparkwitch.roles.killer.magician.UseMagicianAbilityC2SPacket;

/** Compact dye-button command for the four explicit magician state transitions. */
public final class MagicianActionWidget extends ButtonWidget {
    private final ItemStack icon;
    private final Text tooltip;
    private final int action;

    public MagicianActionWidget(int x, int y, ItemStack icon, Text tooltip, int action) {
        super(x, y, 16, 16, tooltip, button -> {
            if (ClientPlayNetworking.canSend(UseMagicianAbilityC2SPacket.ID)) {
                ClientPlayNetworking.send(new UseMagicianAbilityC2SPacket(action));
            }
        }, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        this.tooltip = tooltip;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        context.drawItem(icon, getX(), getY());
        if (isHovered()) {
            int color = 0x90FFBF49;
            context.fillGradient(RenderLayer.getGuiOverlay(), getX(), getY(), getX() + 16, getY() + 16,
                    color, color, 0);
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltip, mouseX, mouseY);
        }
    }
}
