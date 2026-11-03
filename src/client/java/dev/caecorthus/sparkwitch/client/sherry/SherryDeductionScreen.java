package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.net.OpenSherryDeductionS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * 推演面板 — a grid of the players inside reach; picking one confirms the kill and the 200-coin price.
 * 推演面板——射程内的玩家头像网格；点一个即确认击杀并支付 200 金币。
 */
public final class SherryDeductionScreen extends Screen {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 45;
    private static final int ICON = dev.caecorthus.sparkwitch.client.criminalreasoner
            .CriminalReasonerPlayerButton.SIZE;

    private final OpenSherryDeductionS2CPacket roster;
    private final java.util.List<dev.caecorthus.sparkwitch.client.criminalreasoner.CriminalReasonerPlayerButton>
            buttons = new java.util.ArrayList<>();

    public SherryDeductionScreen(OpenSherryDeductionS2CPacket roster) {
        super(Text.translatable("screen.sparkwitch.sherry.title"));
        this.roster = roster;
    }

    @Override
    protected void init() {
        buttons.clear();
        int centerX = width / 2;
        int columns = Math.min(COLUMNS, Math.max(1, roster.targetUuids().size()));
        int startX = centerX - columns * CELL_WIDTH / 2;
        int top = 58;
        for (int index = 0; index < roster.targetUuids().size(); index++) {
            UUID uuid = roster.targetUuids().get(index);
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            var button = new dev.caecorthus.sparkwitch.client.criminalreasoner.CriminalReasonerPlayerButton(
                    startX + column * CELL_WIDTH, top + row * CELL_HEIGHT,
                    uuid, roster.targetNames().get(index), b -> {
                        if (client != null) {
                            SherryClientModule.confirm(client, uuid);
                            close();
                        }
                    });
            buttons.add(button);
            addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.sherry.cancel"), b -> close())
                .dimensions(centerX - 40, height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0xC0101010);
        context.fill(0, 0, 4, height, 0xFF000000 | SherryRules.COLOR);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.sparkwitch.sherry.subtitle", SherryRules.DEDUCTION_COST)
                        .formatted(Formatting.GRAY),
                width / 2, 38, 0xA0A0A0);
        for (var button : buttons) {
            int nameY = button.getY() + ICON + 2;
            String name = button.getMessage().getString();
            String clipped = textRenderer.getWidth(name) > CELL_WIDTH - 4
                    ? textRenderer.trimToWidth(name, CELL_WIDTH - 8) + "…"
                    : name;
            int clipLeft = button.getX() - (CELL_WIDTH - ICON) / 2;
            context.enableScissor(clipLeft, nameY - 1, clipLeft + CELL_WIDTH, nameY + 10);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(clipped),
                    button.getX() + ICON / 2, nameY, 0xFFFFFFFF);
            context.disableScissor();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
