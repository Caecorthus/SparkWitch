package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop.AccompliceShopService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchShopReplayService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchShopService;
import dev.doctor4t.wathe.api.Role;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Facade for Witch faction registration and role-assignment side effects.
 * 魔女阵营注册和身份分配副作用的门面，具体策略由独立 policy 承担。
 */
public final class WitchFactionFeatureService {
    private static boolean registered;

    private WitchFactionFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerEconomyPolicy(WitchFactionEconomyPolicy::economyDecision);
        SparkFactionApi.registerInstinctPolicy(WitchInstinctPolicy::instinctHighlight);
        WitchFactionProtectionPolicy.register();
        // Delay this global Wathe replacement until server startup, after all mod initializers.
        // 将这个 Wathe 全局替换延迟到服务端启动阶段，确保所有模组初始化器已经完成。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> GrandWitchShopReplayService.register());
        GrandWitchShopService.register();
        AccompliceShopService.register();
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchFactionEconomyPolicy.assignStartingLoadout(player, role);
        if (!WitchFactionRules.isGrandWitch(role)) {
            GrandWitchActiveSkillService.clearCeremonialSword(player, false);
        }
    }

    public static void clearPlayerRuntime(ServerPlayerEntity player) {
        GrandWitchActiveSkillService.clearCeremonialSword(player, false);
    }
}
