package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanFerryC2SPayload;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanPardonC2SPayload;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Client senders for the Ferryman: the shared ability key asks the server to pardon a held kill, and the secondary
 * key (2) ferries the body under the crosshair. Both send a request only; the server owns every decision.
 * 摆渡人的客户端发送端：共享技能键请求服务端宽恕被扣住的击杀，第二技能键（2）摆渡准星所指遗体。两者都只发请求，
 * 所有判定由服务端拥有。
 */
public final class FerrymanClient {
    private FerrymanClient() {
    }

    public static void register() {
        FerrymanClientHighlights.register();
        SecondaryAbilityRegistry.register(SparkWitchRoles.FERRYMAN_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                int bodyEntityId = lookedAtBodyId(client);
                if (bodyEntityId == 0) {
                    return;
                }
                if (ClientPlayNetworking.canSend(FerrymanFerryC2SPayload.ID)) {
                    ClientPlayNetworking.send(new FerrymanFerryC2SPayload(bodyEntityId));
                }
            }
        });
    }

    /** Shared ability key press, dispatched by {@code SparkWitchClient} for the exact role only. / 共享技能键按下，由主入口按精确职业分发。 */
    public static void onAbilityKey(MinecraftClient client) {
        if (client.player == null || !ClientPlayNetworking.canSend(FerrymanPardonC2SPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new FerrymanPardonC2SPayload());
    }

    /** True while the local player really holds the Ferryman role. / 本地玩家确实持有摆渡人职业时为 true。 */
    public static boolean isLocalFerryman(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        var role = GameWorldComponent.KEY.get(client.player.getWorld()).getRole(client.player);
        return role != null && FerrymanRules.ROLE_ID.equals(role.identifier());
    }

    private static int lookedAtBodyId(MinecraftClient client) {
        HitResult target = client.crosshairTarget;
        if (target instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerBodyEntity body) {
            return body.getId();
        }
        return 0;
    }
}
