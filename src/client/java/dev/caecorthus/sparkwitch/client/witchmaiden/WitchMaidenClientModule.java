package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.FocusedFootstepsUseResultS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Owns Witch Maiden's connection and per-tick client state. / 管理巫女的连接与客户端逐 tick 状态。 */
public final class WitchMaidenClientModule {
    private static final FocusedFootstepsClientState STATE = new FocusedFootstepsClientState();
    private static boolean registered;

    private WitchMaidenClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(WitchMaidenClientModule::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATE.clearConnection());
        ClientPlayNetworking.registerGlobalReceiver(FocusedFootstepsUseResultS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> onUseResult(payload)));
    }

    public static FocusedFootstepsClientState state() {
        return STATE;
    }

    public static boolean beginRequest(UUID targetUuid, int cooldownTicks) {
        return STATE.beginRequest(targetUuid, cooldownTicks);
    }

    public static int activeWindowTicks() {
        return STATE.remainingEffectTicks();
    }

    /** Accepts only the local owner's decoded CCA sync as acknowledgement. / 只接受本地所有者解码后的 CCA 同步作为确认。 */
    public static void onOwnerSync(WitchPlayerComponent component) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null
                || player.networkHandler == null
                || WitchPlayerComponent.KEY.get(player) != component) {
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        STATE.acknowledgeOwnerSync(
                component.getCooldownTicks(),
                validOwnerState(player, game, component),
                targetUuid -> targetAvailable(player, game, targetUuid)
        );
    }

    /** Consumes the owner-only cast result; the shared component remains the long-term authority. / 消费仅施法者可见的施放结果；长期状态仍以共享组件为准。 */
    private static void onUseResult(FocusedFootstepsUseResultS2CPacket result) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || player.networkHandler == null) {
            STATE.clearRoundState();
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        STATE.resolveUseResult(
                result.accepted(),
                result.cooldownTicks(),
                validOwnerState(player, game, component),
                targetUuid -> targetAvailable(player, game, targetUuid)
        );
    }

    public static void clear() {
        STATE.clearConnection();
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.networkHandler == null || !SparkWitchServerConnection.isConfirmedServer()) {
            STATE.clearConnection();
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        STATE.observe(
                component.getCooldownTicks(),
                validOwnerState(player, game, component),
                targetUuid -> targetAvailable(player, game, targetUuid)
        );
    }

    private static boolean validOwnerState(
            ClientPlayerEntity player,
            GameWorldComponent game,
            WitchPlayerComponent component
    ) {
        return SparkWitchServerConnection.isConfirmedServer()
                && game.isRunning()
                && WitchMaidenRules.isWitchMaiden(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && FocusedFootstepsRules.SKILL_ID.equals(component.getActiveSkillId());
    }

    static boolean targetAvailable(
            ClientPlayerEntity player,
            GameWorldComponent game,
            UUID targetUuid
    ) {
        return !player.getUuid().equals(targetUuid)
                && player.networkHandler.getPlayerListEntry(targetUuid) != null
                && game.hasAnyRole(targetUuid)
                && !game.isPlayerDead(targetUuid);
    }
}
