package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.net.ConfirmSherryDeductionC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenSherryDeductionS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.UUID;

/**
 * 橘雪莉 client half: the deduction panel on the ability key, plus the two-way highlight link with Emma.
 * 橘雪莉客户端：技能键打开推演面板，以及与艾玛的双向透视联动。
 */
public final class SherryClientModule {

    private SherryClientModule() {
    }

    public static void register() {
        SherryInstinctHooks.register();
        // Secondary key: 巨力魔法. / 第二技能键：巨力魔法。
        dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry.register(
                SherryRules.ROLE_ID,
                new dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler() {
                    @Override
                    public void onPressed(MinecraftClient client) {
                        if (ClientPlayNetworking.canSend(
                                dev.caecorthus.sparkwitch.net.UseSherryMightyForceC2SPacket.ID)) {
                            ClientPlayNetworking.send(
                                    new dev.caecorthus.sparkwitch.net.UseSherryMightyForceC2SPacket());
                        }
                    }
                });
        ClientPlayNetworking.registerGlobalReceiver(OpenSherryDeductionS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = context.client();
                    if (client.currentScreen == null) {
                        client.setScreen(new SherryDeductionScreen(payload));
                    }
                }));
        // Sherry's badge-holder highlight only; the Emma link was removed on request.
        // 只保留雪莉对徽章持有者的透视；与艾玛的互透已按要求移除。
        dev.doctor4t.wathe.api.event.GetInstinctHighlight.EVENT.register(target -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity viewer = client.player;
            if (viewer == null || !(target instanceof net.minecraft.client.network.AbstractClientPlayerEntity other)) {
                return null;
            }
            GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
            var viewerRole = game.getRole(viewer);
            if (viewerRole == null) {
                return null;
            }
            String viewerId = viewerRole.identifier().getPath();
            if (SherryRules.ROLE_ID.getPath().equals(viewerId)
                    && SherryComponent.KEY.get(viewer).isBadgeHolder(other.getUuid())) {
                return dev.doctor4t.wathe.api.event.GetInstinctHighlight.HighlightResult.always(SherryRules.BADGE_GLOW);
            }
            return null;
        });
    }

    /** Ability key: ask the server to open the deduction panel. 技能键：请求服务端打开推演面板。 */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !isSherry(player)) {
            return;
        }
        if (ClientPlayNetworking.canSend(dev.caecorthus.sparkwitch.net.RequestSherryDeductionC2SPacket.ID)) {
            ClientPlayNetworking.send(new dev.caecorthus.sparkwitch.net.RequestSherryDeductionC2SPacket());
        }
    }

    public static void confirm(MinecraftClient client, UUID targetId) {
        if (client.player == null || !isSherry(client.player)) {
            return;
        }
        if (ClientPlayNetworking.canSend(ConfirmSherryDeductionC2SPacket.ID)) {
            ClientPlayNetworking.send(new ConfirmSherryDeductionC2SPacket(targetId));
        }
    }

    private static boolean isSherry(ClientPlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && SherryRules.ROLE_ID.equals(role.identifier());
    }
}
