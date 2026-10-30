package dev.caecorthus.sparkwitch.client.knight;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** One avatar button in the Knight duel selector, using Wathe's shop slot frame like the Assassin panel. / 骑士决斗选择器头像按钮，使用 Wathe 商店格子边框，和刺客面板一致。 */
public final class KnightDuelPlayerButton extends ButtonWidget {
    private static final Identifier FRAME = ShopEntry.Type.TOOL.getTexture();
    private static final int SIZE = 30;
    private static final int HEAD = 16;
    private static final int HEAD_OFFSET = 7;
    private final UUID uuid;

    public KnightDuelPlayerButton(int x, int y, UUID uuid, String name, PressAction onPress) {
        super(x, y, SIZE, SIZE, Text.literal(name), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.uuid = uuid;
        setTooltip(Tooltip.of(Text.literal(name)));
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
        if (isHovered()) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x30FFFFFF);
        }
    }
}
