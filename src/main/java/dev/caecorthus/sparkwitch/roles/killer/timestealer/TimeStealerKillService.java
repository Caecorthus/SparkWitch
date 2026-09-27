package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules.SettleActor;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Settles a lethal curse exactly once: a forced, SparkTraits-terminal {@code TIME_STOLEN} kill inside the Judge ledger,
 * then the confirmed-death time penalty and the stamp grant (plan D6).
 * Second owner-approved piercing exception (after the bell toll): {@code force = true} skips Wathe's BEFORE cancels
 * and psycho armour, and the terminal registration skips every SparkTraits protection, so only the Timekeeper can lift
 * the curse beforehand. What still stops the kill: SparkFactionAPI's structural veto while the kill is attributed, a
 * Judge sentence on the Time Stealer's UUID, and the round/match/alive checks. As with the toll, the first protecting
 * Wathe before-kill listener still pays its normal cost before the forced kill proceeds.
 * 对致死诅咒只结算一次：在审判者账本内执行强制、SparkTraits 终结的 {@code TIME_STOLEN} 击杀，
 * 随后在确认死亡后扣除时间并发放邮票（计划 D6）。
 * 继丧钟之后第二个所有者批准的穿透例外：{@code force = true} 跳过 Wathe 的 BEFORE 取消与疯魔护甲，终结原因注册跳过所有
 * SparkTraits 保护，因此只有计时员能提前解除诅咒。仍能拦下击杀的只有：有归属时 SparkFactionAPI 的结构性否决、对窃时者 UUID
 * 的审判者判决，以及对局 / 对局 id / 存活检查。与丧钟一样，第一个出手的 Wathe 击杀前监听保护仍会先付出正常代价，
 * 然后强制击杀照常进行。
 */
public final class TimeStealerKillService {
    private TimeStealerKillService() {
    }

    /**
     * Called by {@code TimeTheftRuntime.tick} after it has already cleared the curse and our Slowness; runs once, never
     * retries. / 由 {@code TimeTheftRuntime.tick} 在已清除诅咒与我们的缓慢之后调用；只执行一次，从不重试。
     */
    static void settle(ServerPlayerEntity victim, UUID stealerUuid) {
        ServerWorld world = victim.getServerWorld();
        MinecraftServer server = world.getServer();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID match = TimeStealerMatch.currentId();
        if (stealerUuid == null || server == null || match == null
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return;
        }
        ServerPlayerEntity stealer = server.getPlayerManager().getPlayer(stealerUuid);
        boolean still = stealer != null && TimeStealerRules.isTimeStealer(game.getRole(stealer));
        boolean allowed = still && SparkFactionApi.canAffectPlayer(stealer, victim, SparkWitchDeathReasons.TIME_STOLEN, game);
        SettleActor actor = TimeStealerRules.settleActor(stealer != null, still, allowed);
        if (actor == SettleActor.VETOED) {
            // The SparkFactionAPI structural veto is honoured as for the toll: the curse is spent, never turned into an
            // unattributed kill. / 与丧钟一样遵循 SparkFactionAPI 结构性否决：诅咒作废，绝不改成无归属击杀。
            return;
        }
        // Offline or no longer the Time Stealer: the curse still kills with no killer (owner decision Q7); a dead but
        // online, still exact Time Stealer stays the killer. / 离线或已不是窃时者：诅咒照样以无击杀者致死（所有者决定 Q7）；
        // 已死亡但在线且仍为精确窃时者时仍记为击杀者。
        ServerPlayerEntity killer = actor == SettleActor.ATTRIBUTED ? stealer : null;
        // runWith keeps the Judge ledger and sentence on the Time Stealer's UUID even when killer is null; force plus the
        // terminal reason is the owner-approved piercing path, with no protection-specific opt-outs here.
        // runWith 即使在击杀者为 null 时也把审判者账本与判决绑定到窃时者 UUID；强制 + 终结原因是所有者批准的穿透路径，
        // 此处不做任何针对具体保护的放行。
        JudgeKillAttribution.runWith(world, stealerUuid,
                () -> GameFunctions.killPlayer(victim, true, killer, SparkWitchDeathReasons.TIME_STOLEN, true));
        // Only a confirmed death in the same ACTIVE match, not intercepted by an older SparkTraits build (fail-closed),
        // moves the round time and grants a stamp. / 只有同一 ACTIVE 对局内确认的死亡、且未被旧版 SparkTraits 拦截
        // （失败关闭）时，才扣除对局时间并发放邮票。
        if (!game.isPlayerDead(victim.getUuid())
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !Objects.equals(match, TimeStealerMatch.currentId())
                || WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        GameTimeComponent time = GameTimeComponent.KEY.get(world);
        // Owner decision Q8: max(0, t - 600); a drain to 0 lets the civilians win on TIME.
        // 所有者决定 Q8：max(0, t - 600)；扣到 0 时平民按 TIME 获胜。
        time.setTime(TimeStealerRules.timeAfterClockKill(time.getTime()));
        // Self-gated by the frozen StampService contract (implemented in WP-05): only a living, exact Time Stealer in
        // the current match receives it, so a dead but online attributed stealer gets nothing (N32).
        // 由冻结的 StampService 契约自带门槛（在 WP-05 实现）：只有当前对局中存活的精确窃时者才能收到，
        // 因此已死亡但在线的被归因窃时者得不到邮票（N32）。
        TimeStealerStampService.grant(killer, 1);
    }
}
