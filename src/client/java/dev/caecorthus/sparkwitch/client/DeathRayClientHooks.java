package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.impl.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.net.FireDeathRayC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client-only left-click bridge for Death Ray; the server still owns all hit validation.
 * 死亡射线的客户端左键桥接；命中判定仍完全由服务端负责。
 */
public final class DeathRayClientHooks {
    private static boolean attackHeld;

    private DeathRayClientHooks() {
    }

    public static void tick(MinecraftClient client) {
        if (client.options == null || !client.options.attackKey.isPressed()) {
            attackHeld = false;
        }
    }

    public static boolean tryFire(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null || !hasActiveDeathRay(player)) {
            return false;
        }
        if (attackHeld) {
            return true;
        }
        attackHeld = true;
        ClientPlayNetworking.send(new FireDeathRayC2SPacket());
        return true;
    }

    private static boolean hasActiveDeathRay(ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        return MurderousWitchDeathRayRules.isDeathRaySkill(component.getActiveSkillId())
                && component.hasActiveDeathRay();
    }
}
