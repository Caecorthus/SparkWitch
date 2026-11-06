package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.net.SaviorDomainS2CPayload;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of the Savior's domain and the psycho-instinct recolor. The domain outline needs no instinct key: members
 * are white, the Savior is the role color. A psycho killer using instinct sees the Savior in the role color as well.
 * 救世主领域的客户端部分与疯魔本能改色。领域描边无需本能键：成员白色，救世主职业色；疯魔杀手按住本能键时，
 * 救世主同样显示为职业色。
 */
public final class SaviorDomainClient {
    private static final int DOMAIN_HIGHLIGHT_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 20;
    private static final int PSYCHO_HIGHLIGHT_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 10;
    private static int saviorEntityId = -1;
    private static long domainEndWorldTime;
    private static boolean registered;

    private SaviorDomainClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SaviorDomainS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.world == null || !SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    saviorEntityId = payload.saviorEntityId();
                    domainEndWorldTime = client.world.getTime() + Math.max(0, payload.durationTicks());
                }));
        GetInstinctHighlight.EVENT.register(SaviorDomainClient::domainHighlight);
        GetInstinctHighlight.EVENT.register(SaviorDomainClient::psychoInstinctHighlight);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void reset() {
        saviorEntityId = -1;
        domainEndWorldTime = 0L;
    }

    /**
     * Domain members see each other without holding instinct: white for everyone else and the Savior's role color for
     * the Savior. The viewer must also be inside the 15-block radius, so outsiders never receive the tags.
     * 领域成员无需按本能键即可互相看见：他人白色，救世主职业色。观察者也必须位于 15 格内，领域外玩家不会看到标记。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult domainHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity viewer = client.player;
        if (viewer == null
                || targetPlayer == viewer
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)) {
            return null;
        }
        PlayerEntity savior = activeSavior(client);
        if (savior == null
                || viewer.squaredDistanceTo(savior) > SaviorRules.DOMAIN_RADIUS_SQUARED
                || targetPlayer.squaredDistanceTo(savior) > SaviorRules.DOMAIN_RADIUS_SQUARED) {
            return null;
        }
        int color = targetPlayer == savior
                ? SaviorRules.ROLE_COLOR
                : SaviorRules.DOMAIN_WHITE_HIGHLIGHT;
        return GetInstinctHighlight.HighlightResult.always(color, DOMAIN_HIGHLIGHT_PRIORITY);
    }

    /** A psycho killer holding instinct sees the Savior in the Savior's role color instead of the default target color. / 疯魔杀手本能下的救世主改显职业色。 */
    private static @Nullable GetInstinctHighlight.HighlightResult psychoInstinctHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || targetPlayer == viewer
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || PlayerPsychoComponent.KEY.get(viewer).getPsychoTicks() <= 0) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!game.canUseKillerFeatures(viewer)) {
            return null;
        }
        Role targetRole = game.getRole(targetPlayer);
        if (!SaviorRules.isSavior(targetRole)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.withKeybind(
                SaviorRules.ROLE_COLOR,
                PSYCHO_HIGHLIGHT_PRIORITY
        );
    }

    private static @Nullable PlayerEntity activeSavior(MinecraftClient client) {
        if (saviorEntityId < 0
                || client.world == null
                || client.world.getTime() >= domainEndWorldTime) {
            return null;
        }
        Entity entity = client.world.getEntityById(saviorEntityId);
        if (!(entity instanceof PlayerEntity savior)
                || !GameFunctions.isPlayerPlayingAndAlive(savior)
                || !SaviorRules.isSavior(GameWorldComponent.KEY.get(savior.getWorld()).getRole(savior))) {
            return null;
        }
        return savior;
    }
}
