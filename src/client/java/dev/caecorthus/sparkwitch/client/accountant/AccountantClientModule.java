package dev.caecorthus.sparkwitch.client.accountant;

import dev.caecorthus.sparkwitch.net.AccountantAuditC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** 会计 client half: the ability key audits. 会计客户端：技能键查账。 */
public final class AccountantClientModule {

    private AccountantClientModule() {
    }

    public static void register() {
    }

    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !AccountantRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        if (ClientPlayNetworking.canSend(AccountantAuditC2SPacket.ID)) {
            ClientPlayNetworking.send(new AccountantAuditC2SPacket());
        }
    }
}
