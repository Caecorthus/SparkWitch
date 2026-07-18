package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.doctor4t.wathe.api.event.DeadPlayerParticipation;

/** Registers active Wraiths as Wathe active-dead participants. */
final class WraithDeadPlayerParticipationService {
    private static boolean registered;

    private WraithDeadPlayerParticipationService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // One provider decision suppresses both mode coercion and dead voice-group insertion.
        // 单一提供方判定同时阻止模式强制与加入死亡语音组。
        DeadPlayerParticipation.EVENT.register(SparkWitchApi::isWraithActive);
    }
}
