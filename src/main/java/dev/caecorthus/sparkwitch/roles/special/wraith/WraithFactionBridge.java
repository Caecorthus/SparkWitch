package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;

/** Installs the Wraith effective-faction override after all mod initializers. */
final class WraithFactionBridge {
    private static boolean callbackRegistered;
    private static boolean resolverRegistered;

    private WraithFactionBridge() {
    }

    static synchronized void register() {
        if (callbackRegistered) {
            return;
        }
        callbackRegistered = true;
        // Late installation makes captured Wraith alignment the final result after optional trait transforms.
        // 延迟安装让冤魂保存的阵营在可选天赋转换后成为最终结果。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> installResolver());
    }

    private static synchronized void installResolver() {
        if (resolverRegistered) {
            return;
        }
        resolverRegistered = true;
        SparkFactionApi.registerEffectiveFactionResolver((player, game, current) -> {
            WraithPlayerComponent wraith = WraithPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (wraith == null || !wraith.isActive() || wraith.getAlignment() == null) {
                return null;
            }
            return factionId(wraith.getAlignment());
        });
    }

    static Identifier factionId(WraithState.Alignment alignment) {
        return switch (alignment) {
            case CIVILIAN -> FactionIds.CIVILIAN;
            case KILLER -> FactionIds.KILLER;
            case WITCH -> SparkWitchFactions.WITCH;
        };
    }
}
