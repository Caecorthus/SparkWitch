package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Decides whether persisted Wraith participation is terminal on reconnect. */
public final class WraithReconnectPolicy {
    private WraithReconnectPolicy() {
    }

    public static boolean shouldTerminateOnJoin(boolean activeWraith) {
        return activeWraith;
    }
}
