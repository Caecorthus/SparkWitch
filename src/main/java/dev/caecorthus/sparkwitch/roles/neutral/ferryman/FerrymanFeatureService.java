package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
        // Body identity stays with the pack's own gate. This role used to answer CanSeeBodyRole with true for every
        // ordinary body, which overrode the coroner-only rule and let any player read a corpse's role and death: the
        // fake corpse already carries a civilian cover, and a real body needs no listener here.
        // 遗体身份交回整合包自己的判定。本角色曾对普通遗体一律回答 true，覆盖了“只有验尸官能看”的规则，导致谁都能读到
        // 死者的身份与死法；假尸体本身已带平民掩护，真实遗体这里不需要任何监听器。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && FerrymanParticipation.isFerryman(role)) {
                FerrymanPlayerComponent.KEY.get(serverPlayer).clearRoundState();
            }
        });
        // Round end needs no per-Charon pass here: Wathe's finalizeGame clears the role map before ON_FINISH_FINALIZE,
        // so a role check there never matches; ResetPlayer (FerrymanPardonService) and RoleAssigned above reset the
        // component, and FerrymanMomentService ends the moment at every round boundary itself.
        // 回合结束无需在此逐个处理摆渡人：Wathe 的 finalizeGame 在 ON_FINISH_FINALIZE 之前就清空了职业表，那里的职业判断
        // 永远不会命中；组件由 ResetPlayer（FerrymanPardonService）与上方的 RoleAssigned 重置，时刻则由
        // FerrymanMomentService 在每个回合边界自行结束。
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
