package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** One server-validated Focused Footsteps target. / 一个仍需服务端复核的聚焦步伐目标。 */
public final class WitchMaidenTargetWidget extends ButtonWidget {
    private final ClientPlayerEntity owner;
    private final UUID targetUuid;
    private @Nullable PlayerListEntry playerListEntry;
    private boolean pageVisible;

    public WitchMaidenTargetWidget(
            int x,
            int y,
            ClientPlayerEntity owner,
            UUID targetUuid,
            @Nullable PlayerListEntry playerListEntry
    ) {
        super(x, y, 16, 16, Text.empty(), button -> {
            if (button instanceof WitchMaidenTargetWidget widget) {
                widget.requestUse();
            }
        }, DEFAULT_NARRATION_SUPPLIER);
        this.owner = owner;
        this.targetUuid = targetUuid;
        this.playerListEntry = playerListEntry;
    }

    public void setPageVisible(boolean visible) {
        pageVisible = visible;
        this.visible = visible;
        updateReadiness();
    }

    public void refreshPlayerListEntry(@Nullable PlayerListEntry entry) {
        playerListEntry = entry;
    }

    @Override
    public void onPress() {
        updateReadiness();
        if (active) {
            super.onPress();
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        updateReadiness();
        int cooldownTicks = displayCooldownTicks();
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        if (!active) {
            context.setShaderColor(0.35F, 0.35F, 0.35F, 0.75F);
        }
        PlayerSkinDrawer.draw(context, resolveSkin().texture(), getX(), getY(), 16);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (cooldownTicks > 0) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            int seconds = cooldownTicks / 20;
            context.drawTextWithShadow(renderer, Integer.toString(seconds), getX(), getY(), 0xFFFF0000);
        }

        if (isHovered()) {
            drawHighlight(context);
            Text name = playerListEntry == null
                    ? Text.literal(targetUuid.toString())
                    : Text.literal(playerListEntry.getProfile().getName());
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            context.drawTooltip(renderer, name, mouseX, mouseY);
        }
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
        // Player skin is the complete button label. / 玩家皮肤就是完整按钮内容。
    }

    private void requestUse() {
        int cooldownTicks = WitchPlayerComponent.KEY.get(owner).getCooldownTicks();
        if (cooldownTicks > 0) {
            return;
        }
        if (WitchMaidenClientModule.beginRequest(targetUuid, cooldownTicks)) {
            ClientPlayNetworking.send(new UseWitchSkillC2SPacket(Optional.of(targetUuid)));
        }
    }

    private void updateReadiness() {
        active = pageVisible
                && !WitchMaidenClientModule.state().isRequestPending()
                && displayCooldownTicks() <= 0;
    }

    private int displayCooldownTicks() {
        int authoritativeTicks = WitchPlayerComponent.KEY.get(owner).getCooldownTicks();
        return WitchMaidenClientModule.state().displayCooldownTicks(authoritativeTicks);
    }

    private SkinTextures resolveSkin() {
        if (playerListEntry != null) {
            return playerListEntry.getSkinTextures();
        }
        return DefaultSkinHelper.getSkinTextures(targetUuid);
    }

    private void drawHighlight(DrawContext context) {
        int color = 0x90B04A8B;
        int x = getX();
        int y = getY();
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
