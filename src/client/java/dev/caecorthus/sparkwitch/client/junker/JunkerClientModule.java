package dev.caecorthus.sparkwitch.client.junker;

import dev.caecorthus.sparkwitch.net.JunkerSearchC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Client half of the 拾荒者: loot vision through walls, and the ability key that starts a corpse search.
 * 拾荒者的客户端：透墙看见掉落物，以及用技能键发起搜刮。
 */
public final class JunkerClientModule {

    private JunkerClientModule() {
    }

    public static void register() {
        // Dropped items glow through walls within the vision radius; nothing else is highlighted.
        // 掉落物在视野半径内透墙发光；其它实体不额外高亮。
        dev.doctor4t.wathe.api.event.GetInstinctHighlight.EVENT.register(target -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player == null || !(target instanceof ItemEntity)) {
                return null;
            }
            if (!isRagpicker(player)) {
                return null;
            }
            double range = JunkerRules.LOOT_VISION_RANGE;
            if (player.squaredDistanceTo(target) > range * range) {
                return null;
            }
            return dev.doctor4t.wathe.api.event.GetInstinctHighlight.HighlightResult.always(JunkerRules.COLOR);
        });
    }

    /** Ability key: start searching the corpse under the crosshair. 技能键：搜刮准星下的尸体。 */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !isRagpicker(player)) {
            return;
        }
        if (!ClientPlayNetworking.canSend(JunkerSearchC2SPacket.ID)) {
            return;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerBodyEntity body) {
            ClientPlayNetworking.send(new JunkerSearchC2SPacket(body.getUuid()));
        }
    }

    private static boolean isRagpicker(ClientPlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && JunkerRules.ROLE_ID.equals(role.identifier());
    }
}
