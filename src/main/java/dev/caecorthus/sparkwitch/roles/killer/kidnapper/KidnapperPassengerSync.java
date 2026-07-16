package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Syncs the corpse passenger relation to the carrier, whom vanilla excludes from tracker broadcasts.
 * 将尸体乘客关系补发给承载者；原版追踪广播会排除承载者自己的客户端。
 */
final class KidnapperPassengerSync {
    private KidnapperPassengerSync() {
    }

    static void send(ServerPlayerEntity carrier) {
        carrier.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(carrier));
    }
}
