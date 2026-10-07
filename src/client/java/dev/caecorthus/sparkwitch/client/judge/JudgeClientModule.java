package dev.caecorthus.sparkwitch.client.judge;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.ConfirmJudgeSelectionC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenJudgeSelectionC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenJudgeSelectionS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Judge's primary-key UI; never registers a secondary key or a Witch inventory skill. / 法官主技能界面，不注册副技能键或魔女背包技能。 */
public final class JudgeClientModule {
    private static final JudgeSelectionState SELECTION = new JudgeSelectionState();

    private JudgeClientModule() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenJudgeSelectionS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (!canConfirm(client)) {
                        clear();
                        return;
                    }
                    if (client.currentScreen != null) {
                        return;
                    }
                    if (SELECTION.accept(payload.sessionId(), payload.playerUuids())) {
                        client.setScreen(new JudgeSelectionScreen(payload));
                    }
                }));
    }

    public static void requestSelection(MinecraftClient client) {
        if (client.currentScreen == null && JudgeClientRules.maySend(
                SparkWitchServerConnection.isConfirmedServer(),
                ClientPlayNetworking.canSend(OpenJudgeSelectionC2SPacket.ID)
                        && ClientPlayNetworking.canSend(ConfirmJudgeSelectionC2SPacket.ID),
                isActiveJudge(client)) && SELECTION.beginRequest()) {
            ClientPlayNetworking.send(new OpenJudgeSelectionC2SPacket());
        }
    }

    public static boolean confirm(MinecraftClient client, UUID sessionId, UUID targetId) {
        if (!canConfirm(client) || !SELECTION.consume(sessionId, targetId)) {
            return false;
        }
        ClientPlayNetworking.send(new ConfirmJudgeSelectionC2SPacket(sessionId, targetId));
        return true;
    }

    public static void cancel(UUID sessionId) {
        if (SELECTION.isPending(sessionId)) {
            SELECTION.clear();
        }
    }

    public static boolean isPending(UUID sessionId) {
        return SELECTION.isPending(sessionId);
    }

    public static void tick(MinecraftClient client) {
        if (!canConfirm(client)) {
            clear();
        } else if (client.currentScreen != null && !(client.currentScreen instanceof JudgeSelectionScreen)) {
            SELECTION.clear();
        } else {
            SELECTION.tick();
        }
    }

    public static void clear() {
        SELECTION.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof JudgeSelectionScreen) {
            client.setScreen(null);
        }
    }

    private static boolean canConfirm(MinecraftClient client) {
        return client.getNetworkHandler() != null && JudgeClientRules.maySend(
                SparkWitchServerConnection.isConfirmedServer(),
                ClientPlayNetworking.canSend(ConfirmJudgeSelectionC2SPacket.ID), isActiveJudge(client));
    }

    /**
     * Bottom-right line gate: the selector gate without the temporary Fear lock, so the line does not blink.
     * 右下角技能行门禁：与选择界面门禁相同，但不含短暂的恐惧锁定，避免提示闪烁。
     */
    public static boolean ownsHud(ClientPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        // isRunning includes STOPPING; no pending selector may survive the match-ending edge.
        // isRunning 包含 STOPPING；待确认界面不能跨越对局结束边界。
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && JudgeRules.isJudge(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !WraithClientState.isRestricted(player);
    }

    private static boolean isActiveJudge(MinecraftClient client) {
        return client.player != null && client.world != null
                && ownsHud(client.player)
                && !GrandWitchFearService.isPlayerFeared(client.player);
    }
}
