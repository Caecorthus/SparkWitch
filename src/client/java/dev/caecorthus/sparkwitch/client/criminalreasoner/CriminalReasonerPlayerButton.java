package dev.caecorthus.sparkwitch.client.criminalreasoner;

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
 * LOCAL REVIEW SHIM for PR #194: copied from the author's fork commit 118a5757 (Criminal Reasoner role, not on main)
 * only because the PR's NatsumeTargetScreen imports it. 本地评审垫片（PR #194）：复制自作者 fork 提交 118a5757（犯罪推理学家
 * 职业，main 上没有），仅因 PR 的 NatsumeTargetScreen 引用了它。
 * <p>One 30×30 avatar tile in the reasoning menu, framed like Wathe's shop slots (the same tile the Knight duel picker
 * uses). The name is drawn underneath by the screen, so the grid can stay on Parox's 36×45 cell.
 * 推理菜单里的 30×30 头像格子，使用 Wathe 商店格子的边框（和骑士决斗选择器同一套）。
 * 名字由界面画在下方，这样网格可以保持 Parox 的 36×45 单元格。
 */
public final class CriminalReasonerPlayerButton extends ButtonWidget {

    private static final Identifier FRAME = ShopEntry.Type.TOOL.getTexture();
    public static final int SIZE = 30;
    private static final int HEAD = 16;
    private static final int HEAD_OFFSET = 7;

    private final UUID uuid;
    private boolean selected;

    public CriminalReasonerPlayerButton(int x, int y, UUID uuid, String name, PressAction onPress) {
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
        if (selected) {
            // Selection sits on top of the frame so it reads even on a hovered tile.
            // 选中框画在边框之上，悬停时也能看清。
            context.drawBorder(getX() - 1, getY() - 1, SIZE + 2, SIZE + 2, 0xFF000000 | SELECTION_COLOR);
        }
        if (isHovered() || selected) {
            context.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x30FFFFFF);
        }
    }

    /** Role colour, matching the menu accent. 与菜单强调色一致的身份色。 */
    // LOCAL REVIEW SHIM (PR #194): CriminalReasonerRules exists only in the author's fork (rm/six-new-roles), so its
    // COLOR value 0x704B4B is inlined here. 本地评审垫片（PR #194）：CriminalReasonerRules 只存在于作者 fork，故在此内联其
    // COLOR 值 0x704B4B。
    public static final int SELECTION_COLOR = 0x704B4B;
}
