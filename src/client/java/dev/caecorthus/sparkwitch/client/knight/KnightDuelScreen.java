package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.net.KnightDuelTargetC2SPayload;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The Knight duel target panel, modelled on NoellesRoles' AssassinScreen. / 仿 NoellesRoles 刺客面板的骑士决斗目标面板。 */
public final class KnightDuelScreen extends Screen {
    private static final int BUTTON = 30;
    private static final int APART = 36;
    private static final int MAX_PER_ROW = 6;
    private final ClientPlayerEntity player;

    public KnightDuelScreen(ClientPlayerEntity player) {
        super(Text.translatable("screen.sparkwitch.knight_duel.title"));
        this.player = player;
    }

    @Override
    protected void init() {
        List<PlayerEntity> players = new ArrayList<>();
        if (client != null && client.world != null && player != null) {
            GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
            for (PlayerEntity candidate : client.world.getPlayers()) {
                if (candidate == player || !candidate.isAlive() || candidate.isSpectator()
                        || game.isPlayerDead(candidate.getUuid())
                        || !GameFunctions.isPlayerPlayingAndAlive(candidate)) {
                    continue;
                }
                players.add(candidate);
            }
        }
        players.sort(Comparator.comparing(PlayerEntity::getNameForScoreboard));
        int perRow = Math.max(1, Math.min(MAX_PER_ROW, players.size()));
        int totalWidth = (perRow - 1) * APART + BUTTON;
        int startX = (width - totalWidth) / 2;
        int startY = 80;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity target = players.get(index);
            int column = index % perRow;
            int row = index / perRow;
            int x = startX + column * APART;
            int y = startY + row * APART;
            addDrawableChild(new KnightDuelPlayerButton(x, y, target.getUuid(), target.getNameForScoreboard(), button -> {
                ClientPlayNetworking.send(new KnightDuelTargetC2SPayload(target.getUuid()));
                close();
            }));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 30, 0x55FFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.sparkwitch.knight_duel.subtitle"),
                width / 2, 48, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
