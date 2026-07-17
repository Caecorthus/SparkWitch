package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.api.event.DeadPlayerParticipation;

/** Registers active Wraiths as Wathe dead-player participants. */
final class WraithDeadPlayerParticipationService {
    private static boolean registered;

    private WraithDeadPlayerParticipationService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DeadPlayerParticipation.EVENT.register(WraithStateService::isActive);
    }
}
