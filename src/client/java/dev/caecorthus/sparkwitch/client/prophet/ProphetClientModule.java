package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.ConfirmProphecyC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenProphecyS2CPacket;
import dev.caecorthus.sparkwitch.net.RequestProphecyC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetProphecySessions;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Prophet's primary-key Prophecy UI. The key sends {@link RequestProphecyC2SPacket} instead of the generic skill
 * packet; the server answers with a session. Prophecy is role-owned presentation: it never registers a secondary
 * key and never renders in the Witch inventory skill panel.
 * 先知主技能键的预言界面。按键发送 {@link RequestProphecyC2SPacket} 而非通用技能包，由服务端回传会话。
 * 预言为职业自有展示：不注册副技能键，也绝不在魔女背包技能面板中显示。
 */
public final class ProphetClientModule {
    private static final ProphetProphecySelectionState SELECTION = new ProphetProphecySelectionState();

    private ProphetClientModule() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenProphecyS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (!canConfirm(client)) {
                        clear();
                        return;
                    }
                    if (client.currentScreen != null) {
                        // Never replace another screen; tell the player why nothing opened.
                        // 绝不替换其他界面；提示玩家为何没有打开。
                        SELECTION.clear();
                        if (client.player != null) {
                            client.player.sendMessage(Text.translatable("message.sparkwitch.prophecy.dropped"), true);
                        }
                        return;
                    }
                    if (SELECTION.accept(payload.sessionId(),
                            payload.candidates().stream().map(OpenProphecyS2CPacket.Candidate::player).toList(),
                            ProphetProphecySessions.SESSION_TICKS)) {
                        client.setScreen(new ProphetProphecyScreen(payload));
                    }
                }));
    }

    /**
     * Whether the primary key belongs to Prophecy: the real Prophet role whose active skill is still Prophecy (a
     * forced debug skill falls through to the generic path).
     * 主技能键是否归预言所有：真实先知职业且当前技能仍为预言（调试强制的其他技能会落到通用路径）。
     */
    public static boolean ownsAbilityKey(PlayerEntity player, Role role) {
        return player != null && ProphetRules.isProphet(role)
                && ProphetRules.PROPHECY_ID.equals(WitchPlayerComponent.KEY.get(player).getActiveSkillId());
    }

    /** Fear is not pre-checked here, so the server answers a Feared press with its usual message. / 此处不预检恐惧，受恐惧时由服务端回复常规提示。 */
    public static void requestProphecy(MinecraftClient client) {
        if (client.currentScreen == null && ProphetProphecyClientRules.maySend(
                SparkWitchServerConnection.isConfirmedServer(),
                ClientPlayNetworking.canSend(RequestProphecyC2SPacket.ID)
                        && ClientPlayNetworking.canSend(ConfirmProphecyC2SPacket.ID),
                isLiveProphet(client)) && SELECTION.beginRequest()) {
            ClientPlayNetworking.send(new RequestProphecyC2SPacket());
        }
    }

    public static boolean confirm(MinecraftClient client, UUID sessionId, UUID victim, ProphetDeathCauseGroup group) {
        if (group == null || !canConfirm(client) || !SELECTION.consume(sessionId, victim)) {
            return false;
        }
        ClientPlayNetworking.send(new ConfirmProphecyC2SPacket(sessionId, victim, group.id()));
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

    public static int remainingTicks() {
        return SELECTION.remainingTicks();
    }

    public static void tick(MinecraftClient client) {
        if (!canConfirm(client)) {
            clear();
        } else if (client.currentScreen != null && !(client.currentScreen instanceof ProphetProphecyScreen)) {
            SELECTION.clear();
        } else if (SELECTION.tick() && client.player != null) {
            client.player.sendMessage(Text.translatable("message.sparkwitch.prophecy.expired"), true);
        }
    }

    public static void clear() {
        SELECTION.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof ProphetProphecyScreen) {
            client.setScreen(null);
        }
    }

    static boolean canConfirm(MinecraftClient client) {
        return client.getNetworkHandler() != null && ProphetProphecyClientRules.maySend(
                SparkWitchServerConnection.isConfirmedServer(),
                ClientPlayNetworking.canSend(ConfirmProphecyC2SPacket.ID),
                isLiveProphet(client) && !GrandWitchFearService.isPlayerFeared(client.player));
    }

    static boolean isLiveProphet(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        // isRunning includes STOPPING; no pending Prophecy may survive the match-ending edge.
        // isRunning 包含 STOPPING；待确认的预言不能跨越对局结束边界。
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && ProphetRules.isProphet(game.getRole(client.player))
                && GameFunctions.isPlayerPlayingAndAlive(client.player)
                && !GameFunctions.isPlayerSpectatingOrCreative(client.player)
                && !WraithClientState.isRestricted(client.player);
    }
}
