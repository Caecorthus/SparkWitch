package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.net.HannaFloatC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** 汉娜 client half: the ability key floats. 汉娜客户端：技能键浮游。 */
public final class HannaClientModule {

    private HannaClientModule() {
    }

    public static void register() {
        HannaInstinctHooks.register();
    }

    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !HannaRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        if (ClientPlayNetworking.canSend(HannaFloatC2SPacket.ID)) {
            ClientPlayNetworking.send(new HannaFloatC2SPacket());
        }
    }
}
