package dev.caecorthus.sparkwitch.roles.civilian.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindKitWiring;
import dev.caecorthus.sparkwitch.roles.civilian.blind.perception.BlindPerceptionWiring;

/**
 * Owns the Blind's server event wiring while SparkWitchEvents stays an aggregator; each area registers its own listeners.
 * 负责盲人的服务端事件接线，SparkWitchEvents 仍只做汇总；各部分自行注册监听器。
 */
public final class BlindFeatureService {
    private static boolean registered;

    private BlindFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlindPerceptionWiring.register();
        BlindKitWiring.register();
    }
}
