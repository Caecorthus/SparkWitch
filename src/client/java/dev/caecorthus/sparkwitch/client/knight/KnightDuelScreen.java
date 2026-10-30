package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.KnightDuelTargetC2SPayload;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
        List<PlayerListEntry> targets = new ArrayList<>();
        ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
        if (network != null && client.world != null && player != null) {
            GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
            // The player list, not world.getPlayers(): living players the client does not track (far down the train)
            // must be pickable too. Display filter only; the server re-validates the pick.
            // 使用玩家列表而非 world.getPlayers()：客户端未追踪的存活玩家（列车远处）也必须可选。仅为显示过滤；服务端会
            // 重新校验所选目标。
            for (PlayerListEntry entry : network.getPlayerList()) {
                UUID uuid = entry.getProfile().getId();
                if (!uuid.equals(player.getUuid()) && isCandidate(game, uuid, entry.getGameMode())) {
                    targets.add(entry);
                }
            }
        }
        targets.sort(Comparator.comparing(entry -> entry.getProfile().getName()));
        int perRow = Math.max(1, Math.min(MAX_PER_ROW, targets.size()));
        int totalWidth = (perRow - 1) * APART + BUTTON;
        int startX = (width - totalWidth) / 2;
        int startY = 80;
        for (int index = 0; index < targets.size(); index++) {
            UUID target = targets.get(index).getProfile().getId();
            int column = index % perRow;
            int row = index / perRow;
            int x = startX + column * APART;
            int y = startY + row * APART;
            addDrawableChild(new KnightDuelPlayerButton(x, y, target, targets.get(index).getProfile().getName(),
                    button -> {
                        ClientPlayNetworking.send(new KnightDuelTargetC2SPayload(target));
                        close();
                    }));
        }
    }

    /**
     * Playing (running round, has a role, not dead in Wathe), survival or adventure, and not a restricted Wraith: the
     * Wraith role covers untracked players, the synced component covers tracked ones.
     * 在局（对局进行中、有职业、Wathe 中未死亡）、生存或冒险模式、且非受限冤魂：冤魂职业覆盖未追踪的玩家，同步组件覆盖已追踪的玩家。
     */
    private boolean isCandidate(GameWorldComponent game, UUID uuid, GameMode mode) {
        if (!game.isRunning() || !game.hasAnyRole(uuid) || game.isPlayerDead(uuid)
                || mode == null || !mode.isSurvivalLike() || game.getRole(uuid) == SparkWitchRoles.wraith()) {
            return false;
        }
        PlayerEntity tracked = client.world.getPlayerByUuid(uuid);
        return tracked == null || tracked.isAlive() && !WraithClientState.isRestricted(tracked);
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
