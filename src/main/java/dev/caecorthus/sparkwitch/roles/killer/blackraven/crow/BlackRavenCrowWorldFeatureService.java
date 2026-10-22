package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.GameEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Event wiring for Raven Form's world side (crow-world): the body locks, the Homing Feather tickets, the landing on
 * {@link BlackRavenCrowEvents#ENDED} and its hold, the blackout exemption, and the round-end sweep. Runs from the
 * common initializer; only the server acts (the body-lock callbacks also answer on the owner's client).
 * 化鸦世界侧（crow-world）的事件接线：本体锁、归羽区块票据、{@link BlackRavenCrowEvents#ENDED} 时的落地与定身、熄灯豁免以及回合结束清扫。
 * 在通用初始化器中运行；只有服务端执行（本体锁回调在主人客户端上也会应答）。
 */
public final class BlackRavenCrowWorldFeatureService {
    private static boolean registered;

    private BlackRavenCrowWorldFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackRavenCrowBodyGuards.register();
        BlackRavenCrowEvents.ENDED.register(BlackRavenCrowLandingService::onEnded);
        // In crow form the camera is the crow, which sees in the dark (plan §2.9): no blackout blindness is applied
        // while flying. An existing blindness (Perception's own) is left alone; Wathe re-applies the blackout every
        // tick, so a session ending mid-blackout is blind again like any police.
        // 化鸦期间镜头是乌鸦，能在黑暗中视物（计划 §2.9）：飞行时不施加熄灯致盲。已有的失明（感知自身的）保持不动；Wathe 每刻重新施加熄灯，
        // 因此会话在熄灯中途结束后会像其他警察一样再次失明。
        BlackoutEffect.BEFORE.register((player, durationTicks) ->
                BlackRavenCrowForm.isInCrowForm(player) ? BlackoutEffect.BlackoutResult.cancel() : null);
        ServerTickEvents.END_WORLD_TICK.register(BlackRavenCrowFeatherService::tickWorld);
        ServerTickEvents.END_SERVER_TICK.register(BlackRavenCrowLandingService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BlackRavenCrowLandingService.forget(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BlackRavenCrowLandingService.forgetAll());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                BlackRavenCrowLandingService.forget(player.getUuid());
            }
            BlackRavenCrowFeatherService.discardAll(serverWorld);
        });
    }
}
