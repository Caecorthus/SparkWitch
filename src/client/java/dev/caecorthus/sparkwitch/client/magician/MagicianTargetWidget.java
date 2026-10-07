package dev.caecorthus.sparkwitch.client.magician;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/** 在线玩家头像槽位的简化实现；点击后通过服务端 UUID 选择皮套目标。 */
public final class MagicianTargetWidget extends ButtonWidget {
    private final PlayerListEntry entry;
    public MagicianTargetWidget(int x,int y,PlayerListEntry entry){ super(x,y,16,16,Text.empty(),b->MagicianTargetSelectionApi.select(entry.getProfile().getId()),DEFAULT_NARRATION_SUPPLIER); this.entry=entry; }
    @Override protected void renderWidget(DrawContext context,int mouseX,int mouseY,float delta){
        var client=MinecraftClient.getInstance();
        // 先保留 Wathe 按钮的标准交互层，再覆盖玩家头像槽位材质。
        super.renderWidget(context, mouseX, mouseY, delta);
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        PlayerSkinDrawer.draw(context, entry.getSkinTextures(), getX(), getY(), 16);
        var component=client.player == null ? null : dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent.KEY.get(client.player);
        if(component != null && (component.selectedTarget().equals(entry.getProfile().getId()) || isHovered())) drawShopSlotHighlight(context, getX(), getY());
        if(isHovered()) context.drawTooltip(client.textRenderer, Text.literal(entry.getProfile().getName()), getX() - 4 - client.textRenderer.getWidth(Text.literal(entry.getProfile().getName())) / 2, getY() - 9);
    }
    private void drawShopSlotHighlight(DrawContext context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
