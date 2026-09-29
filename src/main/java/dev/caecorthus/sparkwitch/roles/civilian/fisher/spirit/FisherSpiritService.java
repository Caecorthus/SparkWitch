package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import net.minecraft.server.network.ServerPlayerEntity;

/** Glimmerfish window: start, tick, end, door exit. / 灵光鱼窗口：开始、计时、结束、出门推离。WP4 stub. */
public final class FisherSpiritService {
    private FisherSpiritService() {
    }

    public static void register() {
    }

    /** Server only. Starts (or restarts) the 156-tick window; returns whether it started. / 仅服务端。 */
    public static boolean start(ServerPlayerEntity player) {
        return false;
    }
}
