package dev.caecorthus.sparkwitch.client.medium;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * One avatar button in the 招魂 target panel, using Wathe's shop slot frame and the player's head like the Knight duel
 * selector. Bound targets stay visible but dimmed and inert.
 * 招魂目标面板的头像按钮，使用 Wathe 商店格子边框与玩家头颅，样式与骑士决斗选择器一致。已绑定目标仍然可见，但压暗且不可点。
 */
public final class MediumHauntPlayerButton extends ButtonWidget {
    private static final Identifier FRAME = ShopEntry.Type.TOOL.getTexture();
    private static final int SIZE = 30;
    private static final int HEAD = 16;
    private static final int HEAD_OFFSET = 7;
    private static final int SELECTED_BORDER = 0xFF54E0B8;
    private static final int BOUND_OVERLAY = 0x99000000;
    private final UUID uuid;
    private final boolean bound;
    private boolean selected;

    public MediumHauntPlayerButton(int x, int y, UUID uuid, String name, boolean bound, PressAction onPress) {
        super(x, y, SIZE, SIZE, Text.literal(name), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.uuid = uuid;
        this.bound = bound;
        Text tooltip = Text.literal(name);
        if (bound) {
            tooltip = tooltip.copy().append(Text.translatable("screen.sparkwitch.medium.haunt.bound"));
        }
        setTooltip(Tooltip.of(tooltip));
        this.active = !bound;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(FRAME, getX(), getY(), SIZE, SIZE);
        PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler() == null ? null
                : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(uuid);
        if (entry != null) {
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(getX() + HEAD_OFFSET, getY() + HEAD_OFFSET, 0);
            matrices.scale(HEAD / 8.0F, HEAD / 8.0F, 1.0F);
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 8, 8, 8, 8, 64, 64);
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 40, 8, 8, 8, 64, 64);
            matrices.pop();
        }
        if (bound) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, BOUND_OVERLAY);
        }
        if (selected) {
            context.drawBorder(getX(), getY(), SIZE, SIZE, SELECTED_BORDER);
        }
        if (isHovered() && !bound) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x30FFFFFF);
        }
    }
}
