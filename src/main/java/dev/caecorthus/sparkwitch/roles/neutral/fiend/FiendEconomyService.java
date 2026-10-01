package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Fiend gold visibility: a neutral is not a killer, so Wathe hides the coin counter unless this listener allows it.
 * The Fiend has no task money and no starting money (Wathe resets every balance to 0 at round start); gold comes only
 * from hit reactions. Both sides: the role and Wathe's alive state are synced to every client.
 * 魔人金币可见性：中立不是杀手，除非本监听器允许，否则 Wathe 会隐藏金币计数。魔人没有任务收入和开局金币
 * （Wathe 在开局时将所有余额重置为 0），金币只来自受击反应。两端通用：职业与 Wathe 存活状态均同步到所有客户端。
 */
public final class FiendEconomyService {
    private static boolean registered;

    private FiendEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(FiendEconomyService::canSeeMoney);
    }

    static @Nullable CanSeeMoney.Result moneyVisibilityResult(boolean fiend, boolean playingAndAlive) {
        return fiend && playingAndAlive ? CanSeeMoney.Result.ALLOW : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return moneyVisibilityResult(FiendParticipation.isFiend(player), GameFunctions.isPlayerPlayingAndAlive(player));
    }
}
