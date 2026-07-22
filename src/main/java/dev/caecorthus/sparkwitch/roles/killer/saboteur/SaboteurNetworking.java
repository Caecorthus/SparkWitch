package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the dedicated Saboteur request without entering the Witch skill dispatcher.
 * 注册破坏者专用请求，不进入魔女技能分发路径。
 */
public final class SaboteurNetworking {
    private static boolean registered;

    private SaboteurNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseSaboteurSkillC2SPacket.ID, UseSaboteurSkillC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                UseSaboteurSkillC2SPacket.ID,
                (payload, context) -> dispatch(context.player(), SaboteurAbilityService::use)
        );
    }

    static <P> boolean dispatch(P player, java.util.function.Predicate<P> ability) {
        return ability.test(player);
    }
}
