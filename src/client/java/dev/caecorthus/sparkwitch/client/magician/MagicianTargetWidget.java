package dev.caecorthus.sparkwitch.client.magician;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;

/**
 * 名单玩家头像槽位的简化实现；点击后通过服务端 UUID 选择皮套目标。
 * The head comes from the tab-list entry while that player is online, else the default skin for the UUID.
 * 头像在该玩家在线时取自玩家列表条目，否则使用该 UUID 的默认皮肤。
 */
public final class MagicianTargetWidget extends ButtonWidget {
    private final MagicianPlayerComponent.RosterEntry entry;
    public MagicianTargetWidget(int x,int y,MagicianPlayerComponent.RosterEntry entry){ super(x,y,16,16,Text.empty(),b->MagicianTargetSelectionApi.select(entry.uuid()),DEFAULT_NARRATION_SUPPLIER); this.entry=entry; }
    @Override protected void renderWidget(DrawContext context,int mouseX,int mouseY,float delta){
        var client=MinecraftClient.getInstance();
        // 先保留 Wathe 按钮的标准交互层，再覆盖玩家头像槽位材质。
        super.renderWidget(context, mouseX, mouseY, delta);
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        PlayerSkinDrawer.draw(context, skin(client), getX(), getY(), 16);
        var component=client.player == null ? null : MagicianPlayerComponent.KEY.get(client.player);
        if(component != null && (component.selectedTarget().equals(entry.uuid()) || isHovered())) drawShopSlotHighlight(context, getX(), getY());
        if(isHovered()) context.drawTooltip(client.textRenderer, Text.literal(entry.name()), getX() - 4 - client.textRenderer.getWidth(Text.literal(entry.name())) / 2, getY() - 9);
    }
    private SkinTextures skin(MinecraftClient client) {
        PlayerListEntry online = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(entry.uuid());
        return online == null ? DefaultSkinHelper.getSkinTextures(entry.uuid()) : online.getSkinTextures();
    }
    private void drawShopSlotHighlight(DrawContext context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
