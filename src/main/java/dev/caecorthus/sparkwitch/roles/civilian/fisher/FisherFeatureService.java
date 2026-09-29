package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritService;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishStabService;

/**
 * Owns Angler event wiring while SparkWitchEvents stays an aggregator. Each service registers its own listeners.
 * 负责钓鱼佬的事件接线，SparkWitchEvents 仍只做汇总。各服务自行注册监听器。
 */
public final class FisherFeatureService {
    private static boolean registered;

    private FisherFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FisherShopService.register();
        FisherEconomyService.register();
        FisherFishingService.register();
        FisherKeyFishDoors.register();
        FisherPufferfishService.register();
        FisherLifecycleService.register();
        SwordfishStabService.register();
        FisherSpiritService.register();
    }
}
