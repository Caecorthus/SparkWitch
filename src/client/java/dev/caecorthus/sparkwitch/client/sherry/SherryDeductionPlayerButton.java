package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
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
 * One avatar button in the 推演 candidate panel, in the Medium 招魂 / Knight duel style (Wathe's shop slot frame and
 * the player's head). A candidate she already tracks keeps a thin identity-colour frame and a tooltip note, from her
 * own owner-synced state only, and stays selectable: a badge killer on the 40 s cycle can still be deduced, while the server refuses a target
 * already on the 20 s cycle before any charge.
 * 推演候选面板的头像按钮，样式与通灵师招魂、骑士决斗一致（Wathe 商店格子边框与玩家头颅）。已在追踪中的候选保留一圈细的身份色
 * 边框与提示文字（只来自仅同步给本人的状态），并且仍可选择：处于 40 秒周期的徽章凶手仍可被推演，
 * 已处于 20 秒周期的目标会被服务端在扣费前拒绝。
 */
public final class SherryDeductionPlayerButton extends ButtonWidget {
    private static final Identifier FRAME = ShopEntry.Type.TOOL.getTexture();
    private static final int SIZE = 30;
    private static final int HEAD = 16;
    private static final int HEAD_OFFSET = 7;
    private static final int SELECTED_BORDER = 0xFF000000 | SherryRules.COLOR;
    private static final int TRACKED_BORDER = 0x80000000 | SherryRules.COLOR;
    private final UUID uuid;
    private final boolean tracked;
    private boolean selected;

    public SherryDeductionPlayerButton(int x, int y, UUID uuid, String name, boolean tracked, PressAction onPress) {
        super(x, y, SIZE, SIZE, Text.literal(name), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.uuid = uuid;
        this.tracked = tracked;
        Text tooltip = Text.literal(name);
        if (tracked) {
            tooltip = tooltip.copy().append(Text.translatable("screen.sparkwitch.sherry.deduction.tracked"));
        }
        setTooltip(Tooltip.of(tooltip));
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
            // Face, then the hat layer. / 先画脸，再画帽子层。
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 8, 8, 8, 8, 64, 64);
            context.drawTexture(entry.getSkinTextures().texture(), 0, 0, 40, 8, 8, 8, 64, 64);
            matrices.pop();
        }
        if (selected) {
            context.drawBorder(getX(), getY(), SIZE, SIZE, SELECTED_BORDER);
        } else if (tracked) {
            context.drawBorder(getX(), getY(), SIZE, SIZE, TRACKED_BORDER);
        }
        if (isHovered()) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x30FFFFFF);
        }
    }
}
