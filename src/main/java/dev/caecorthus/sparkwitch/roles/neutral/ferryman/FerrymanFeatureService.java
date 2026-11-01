package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.api.event.CanSeeBodyRole;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * The Ferryman's single registration hub: one line in {@code SparkWitchEvents}, every subsystem appended here.
 * 摆渡人的唯一注册中心：在 {@code SparkWitchEvents} 中占一行，各子系统在此追加。
 */
public final class FerrymanFeatureService {
    private static boolean registered;

    private FerrymanFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FerrymanNetworking.register();
        FerrymanShopService.register();
        FerrymanPardonService.register();
        FerrymanFeignService.register();
        FerrymanMomentService.register();
        FerrymanWinService.register();
        CanSeeMoney.EVENT.register(FerrymanFeatureService::canSeeMoney);
        // A feigning player's fake corpse must not be examined: the role it would show is a cover, and the owner spec
        // keeps the coroner out of it altogether.
        // 假死者的假尸体不可被查验：它能显示的只是掩护身份，而所有者设定要求验尸官完全查不到。
        CanSeeBodyRole.EVENT.register(entity ->
                !(entity instanceof PlayerBodyEntity body) || !FerrymanFeignService.isFakeBody(body));
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && FerrymanParticipation.isFerryman(role)) {
                FerrymanPlayerComponent.KEY.get(serverPlayer).clearRoundState();
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            FerrymanMomentService.end(serverWorld);
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (FerrymanParticipation.isFerryman(player)) {
                    FerrymanPlayerComponent.KEY.get(player).clearRoundState();
                }
            }
        });
    }

    /**
     * A neutral is not a killer, so Wathe hides the coin counter unless a listener allows it; the Ferryman earns its
     * shop money from ferrying, so it must see the balance.
     * 中立不是杀手，除非监听器允许，Wathe 会隐藏金币计数；摆渡人的商店钱来自摆渡，因此必须能看到余额。
     */
    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return FerrymanParticipation.isFerryman(player) && GameFunctions.isPlayerPlayingAndAlive(player)
                ? CanSeeMoney.Result.ALLOW
                : null;
    }
}
