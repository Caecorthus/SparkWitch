package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkfactionapi.api.FactionDefinition;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationHudLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationSnapshotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Renders the server-issued faction snapshot without recalculating live counts on the client.
 * 只渲染服务端下发的阵营快照，客户端不会实时重算人数。
 */
public final class TarotDivinationHudRenderer {
    private static final int RIGHT_PADDING = 12;
    private static final int WHITE = 0xFFFFFF;

    private TarotDivinationHudRenderer() {
    }

    public static void render(DrawContext context) {
        TarotDivinationSnapshotState.Snapshot snapshot = TarotDivinationClientState.snapshotState()
                .snapshot()
                .orElse(null);
        if (snapshot == null) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text title = Text.translatable("hud.sparkwitch.tarot.title").formatted(Formatting.BOLD);
        drawRightAligned(context, renderer, title, TarotDivinationHudLayout.titleY(renderer.fontHeight), WHITE);

        int rowIndex = 0;
        for (TarotDivinationHudLayout.Row row : TarotDivinationHudLayout.rows(snapshot)) {
            Text text = Text.translatable(translationKey(row.faction()), row.count());
            drawRightAligned(
                    context,
                    renderer,
                    text,
                    TarotDivinationHudLayout.rowY(rowIndex, renderer.fontHeight),
                    factionColor(factionId(row.faction()))
            );
            rowIndex++;
        }
    }

    private static void drawRightAligned(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int y,
            int color
    ) {
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(text);
        context.drawTextWithShadow(renderer, text, x, y, color);
    }

    private static String translationKey(TarotDivinationHudLayout.FactionSlot faction) {
        return switch (faction) {
            case CIVILIAN -> "hud.sparkwitch.tarot.civilian";
            case KILLER -> "hud.sparkwitch.tarot.killer";
            case NEUTRAL -> "hud.sparkwitch.tarot.neutral";
            case WITCH -> "hud.sparkwitch.tarot.witch";
        };
    }

    private static Identifier factionId(TarotDivinationHudLayout.FactionSlot faction) {
        return switch (faction) {
            case CIVILIAN -> FactionIds.CIVILIAN;
            case KILLER -> FactionIds.KILLER;
            case NEUTRAL -> FactionIds.NEUTRAL;
            case WITCH -> SparkWitchFactions.WITCH;
        };
    }

    private static int factionColor(Identifier factionId) {
        return SparkFactionApi.getFaction(factionId)
                .map(FactionDefinition::color)
                .orElse(WHITE);
    }
}
