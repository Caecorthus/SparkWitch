package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.doctor4t.wathe.util.GunShootPayload;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Rejects Wathe target payloads before they spend items or emit effects for an isolated Vendetta pair.
 * 在 Wathe 目标数据包消耗物品或生成效果前，拒绝不属于仇杀绑定双方的请求。
 */
public final class VendettaTargetingPacketGuard {
    private VendettaTargetingPacketGuard() {
    }

    public static boolean shouldBlock(ServerPlayerEntity actor, CustomPayload payload) {
        int targetEntityId;
        if (payload instanceof KnifeStabPayload knife) {
            targetEntityId = knife.target();
        } else if (payload instanceof GunShootPayload gun) {
            targetEntityId = gun.target();
        } else {
            return false;
        }

        Entity entity = actor.getServerWorld().getEntityById(targetEntityId);
        if (!(entity instanceof ServerPlayerEntity target)) {
            return false;
        }
        boolean vendettaEndpoint = VendettaInteractionService.isActiveVendetta(actor)
                || VendettaInteractionService.isActiveVendetta(target);
        return vendettaEndpoint && !VendettaInteractionService.isExactPair(actor, target);
    }
}
