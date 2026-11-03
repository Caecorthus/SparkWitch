package dev.caecorthus.sparkwitch.client.noa;

import dev.caecorthus.sparkwitch.net.NoaPaintC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** 诺亚 client half: the ability key asks the server to paint. 诺亚客户端：技能键请求作画。 */
public final class NoaClientModule {

    private NoaClientModule() {
    }

    public static void register() {
    }

    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !NoaRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        if (ClientPlayNetworking.canSend(NoaPaintC2SPacket.ID)) {
            ClientPlayNetworking.send(new NoaPaintC2SPacket());
        }
    }
}
