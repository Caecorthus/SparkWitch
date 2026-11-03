package dev.caecorthus.sparkwitch.client.criminalreasoner;

import dev.caecorthus.sparkwitch.net.ConfirmCriminalReasonerC2SPacket;
import dev.caecorthus.sparkwitch.net.CriminalReasonerInvisibilityC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenCriminalReasonerS2CPacket;
import dev.caecorthus.sparkwitch.net.RequestCriminalReasonerC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.UUID;

/**
 * Client half of the Criminal Reasoner: the reasoning menu on the shared ability key, and the invisibility charge on
 * the secondary ability key.
 * 犯罪推理学家的客户端：主技能键打开推理菜单，第二技能键消耗隐身充能。
 */
public final class CriminalReasonerClientModule {

    private static boolean pendingRequest;
    private static long pendingSince;

    private CriminalReasonerClientModule() {
    }

    public static void register() {
        // Instinct highlights are evaluated on the client only, so the invisibility shield has to live here.
        // 本能高亮只在客户端求值，因此隐身屏蔽必须注册在这里（放在服务端不会生效）。
        dev.doctor4t.wathe.api.event.GetInstinctHighlight.EVENT.register(target -> {
            if (target instanceof net.minecraft.entity.player.PlayerEntity player && player.isInvisible()) {
                return dev.doctor4t.wathe.api.event.GetInstinctHighlight.HighlightResult.skip();
            }
            return null;
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenCriminalReasonerS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    pendingRequest = false;
                    MinecraftClient client = context.client();
                    if (client.currentScreen != null || client.player == null) {
                        return;
                    }
                    client.setScreen(new CriminalReasonerScreen(payload));
                }));
        dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry.register(
                CriminalReasonerRules.ROLE_ID,
                new dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler() {
                    @Override
                    public void onPressed(MinecraftClient client) {
                        if (ClientPlayNetworking.canSend(CriminalReasonerInvisibilityC2SPacket.ID)) {
                            ClientPlayNetworking.send(new CriminalReasonerInvisibilityC2SPacket());
                        }
                    }

                    @Override
                    public void reset() {
                        pendingRequest = false;
                    }
                });
    }

    /** Primary ability key: ask the server for the roster. 主技能键：向服务端要名单。 */
    public static void requestSelection(MinecraftClient client) {
        // A request that never came back must not lock the key forever. / 没有回音的请求不能永久锁死这个键。
        if (pendingRequest && System.currentTimeMillis() - pendingSince > 5000L) {
            pendingRequest = false;
        }
        if (pendingRequest
                || client.currentScreen != null
                || !SparkWitchServerConnection.isConfirmedServer()
                || !ClientPlayNetworking.canSend(RequestCriminalReasonerC2SPacket.ID)
                || !isActiveReasoner(client)) {
            return;
        }
        pendingRequest = true;
        pendingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new RequestCriminalReasonerC2SPacket());
    }

    public static void confirm(MinecraftClient client, UUID sessionId, UUID victimId, UUID suspectId) {
        if (!ClientPlayNetworking.canSend(ConfirmCriminalReasonerC2SPacket.ID) || !isActiveReasoner(client)) {
            return;
        }
        ClientPlayNetworking.send(new ConfirmCriminalReasonerC2SPacket(sessionId, victimId, suspectId));
    }

    public static void clear() {
        pendingRequest = false;
    }

    private static boolean isActiveReasoner(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return false;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && CriminalReasonerRules.ROLE_ID.equals(role.identifier());
    }
}
