package dev.caecorthus.sparkwitch.client.timestealer;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.ClockGeometry;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerClockItem;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client crosshair hint for the Clock. It reuses the server's {@code ClockGeometry} but filters candidates by public
 * state only, so the hint never reveals a hidden role, trait or faction veto; the server still decides every theft.
 * 时钟的客户端准星提示。复用服务端的 {@code ClockGeometry}，但只按公开状态过滤候选者，因此提示永远不会暴露隐藏的
 * 职业、词条或阵营否决；是否窃取始终由服务端决定。
 */
public final class TimeStealerClientTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");

    private TimeStealerClientTargeting() {
    }

    /** Ready Clock in the local Time Stealer's main hand with a visible player on the ray. / 本地窃时者主手持就绪时钟且射线上有可见玩家。 */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        // Only our own item answers; any other held item leaves the chained crosshair value untouched.
        // 只处理自己的道具；手持其他物品时不改动串联的准星值。
        if (!(stack.getItem() instanceof TimeStealerClockItem clock)
                || player.getItemCooldownManager().isCoolingDown(clock)
                || !TimeStealerRules.isTimeStealer(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return false;
        }
        return ClockGeometry.findTarget(player, TimeStealerRules.CLOCK_RANGE, player.clientWorld.getPlayers(),
                candidate -> candidate != player && isVisibleCandidate(candidate)) != null;
    }

    /**
     * Same public filter as Wathe's own gun crosshair, plus the synced Wraith state: the server treats an active Wraith
     * as transparent to the Clock, and CONTEXT's Wraith rule skips it as an aim or crosshair target, so the hint never
     * lights on (and never locates) a Wraith hidden from this viewer.
     * 与 Wathe 自身枪械准星相同的公开过滤，外加已同步的冤魂状态：服务端对时钟而言视激活冤魂为透明，CONTEXT 的冤魂规则
     * 也把它跳过为瞄准或准星目标，因此提示绝不会在对本观察者隐藏的冤魂上亮起（也不会暴露其位置）。
     */
    static boolean isVisibleCandidate(PlayerEntity candidate) {
        return candidate.isAlive() && GameFunctions.isPlayerAliveAndSurvival(candidate) && !candidate.isInvisible()
                && !WraithClientState.isActive(candidate);
    }
}
