package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.compat.SparkStrengthM67Compat;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceAttackHandlers;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerInteractionGuards;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteStreaming;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie.SeekerTaotieService;

/**
 * Seeker registration hub: the single line in SparkWitchEvents. Each work package owns its own {@code register()};
 * the callback phase ordering is installed before any of them registers a listener.
 * 搜寻者注册中心：SparkWitchEvents 中唯一的一行。各工作包拥有自己的 {@code register()}；
 * 在任何监听器注册之前先安装回调阶段顺序。
 */
public final class SeekerFeatureService {
    private static boolean registered;

    private SeekerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SeekerCallbackPhases.ensureOrdered();
        SeekerLifecycleService.register();
        SeekerLoadoutService.register();
        SeekerShopService.register();
        SeekerEconomyService.register();
        SeekerReplayFormatters.register();
        SeekerDeviceService.register();
        SeekerDeviceAttackHandlers.register();
        SparkStrengthM67Compat.register();
        SeekerTaotieService.register();
        SeekerRemoteSessionService.register();
        SeekerRemoteStreaming.register();
        SeekerInteractionGuards.register();
    }
}
