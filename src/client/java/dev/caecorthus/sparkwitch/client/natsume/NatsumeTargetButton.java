package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * One avatar tile in 夏目安安's hypnosis menu, modelled on the Knight duel's {@code KnightDuelPlayerButton}: Wathe's
 * shop slot frame, the player-list head plus hat layer, a hover wash, and a {@code C5B4FF} selection border on top.
 * The name is only the tooltip (owner 2026-11-03: no labels under the tiles).
 * 夏目安安催眠菜单中的一个头像格，仿骑士决斗的 {@code KnightDuelPlayerButton}：Wathe 商店格子边框、玩家列表头像加帽子层、
 * 悬停高亮，以及画在最上层的 {@code C5B4FF} 选中边框。名字只作为 tooltip（所有者 2026-11-03：格子下方不画名字）。
 */
public final class NatsumeTargetButton extends ButtonWidget {
    public static final int SIZE = 30;
    private static final Identifier FRAME = ShopEntry.Type.TOOL.getTexture();
    private static final int HEAD = 16;
    private static final int HEAD_OFFSET = 7;
    private static final int HOVER_WASH = 0x30FFFFFF;
    private static final int SELECTION_BORDER = 0xFF000000 | NatsumeRules.COLOR;
    private final UUID uuid;
    private boolean selected;

    public NatsumeTargetButton(int x, int y, UUID uuid, String name, PressAction onPress) {
        super(x, y, SIZE, SIZE, Text.literal(name), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.uuid = uuid;
        setTooltip(Tooltip.of(Text.literal(name)));
    }

    public UUID playerUuid() {
        return uuid;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(FRAME, getX(), getY(), SIZE, SIZE);
        ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(uuid);
        if (entry != null) {
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(getX() + HEAD_OFFSET, getY() + HEAD_OFFSET, 0);
            matrices.scale(HEAD / 8.0F, HEAD / 8.0F, 1.0F);
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 8, 8, 8, 8, 64, 64);
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 40, 8, 8, 8, 64, 64);
            matrices.pop();
        }
        if (isHovered() || selected) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, HOVER_WASH);
        }
        if (selected) {
            // Drawn last so the border also reads on a hovered tile. / 最后绘制，悬停时边框也清晰可见。
            context.drawBorder(getX() - 1, getY() - 1, SIZE + 2, SIZE + 2, SELECTION_BORDER);
        }
    }
}
