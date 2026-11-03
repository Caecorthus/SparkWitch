package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.net.OpenNatsumeMenuS2CPacket;
import dev.caecorthus.sparkwitch.net.RequestNatsumeMenuC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** 夏目安安 client half: the ability key asks the server to hypnotise. 夏目安安客户端：技能键请求催眠。 */
public final class NatsumeClientModule {

    private NatsumeClientModule() {
    }

    public static void register() {
        NatsumeInstinctHooks.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OpenNatsumeMenuS2CPacket.ID, (payload, context) ->
                        context.client().execute(() ->
                                context.client().setScreen(new NatsumeTargetScreen(
                                        payload.targets(), payload.names()))));
    }

    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !NatsumeRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        if (NatsumeComponent.KEY.get(player).isHypnotised()) {
            return;
        }
        if (ClientPlayNetworking.canSend(RequestNatsumeMenuC2SPacket.ID)) {
            ClientPlayNetworking.send(new RequestNatsumeMenuC2SPacket());
        }
    }
}
