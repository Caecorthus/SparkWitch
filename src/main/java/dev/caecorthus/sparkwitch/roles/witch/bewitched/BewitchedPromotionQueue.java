package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pending Bewitched promotions (C3). Wathe fires {@code TaskComplete} while it iterates the player's task map, so the
 * listener only enqueues; the role change runs at {@code END_SERVER_TICK} after a fresh validation, in enqueue order
 * (a promotion's "used" mark is therefore visible to the next one). Entries for a player who cannot safely change
 * role yet (offline, inside a Rift Gate, swallowed by a Taotie, hijacked by a Kidnapper, afflicted by a Hunter trap)
 * stay queued; stale entries are dropped. Server thread only; cleared at round start, round end and on
 * {@code ResetPlayer}.
 * 待处理的魔化使晋升（C3）。Wathe 在遍历玩家任务表时触发 {@code TaskComplete}，因此监听器只负责入队；身份变更在
 * {@code END_SERVER_TICK} 重新校验后按入队顺序执行（前一次晋升的"已使用"标记对下一次可见）。暂时无法安全变更身份的玩家
 * （离线、位于裂隙门内、被饕餮吞下、被绑架者劫持、受猎人陷阱影响）保留在队列中；过期条目被丢弃。仅服务端线程；开局、
 * 局末与 {@code ResetPlayer} 时清空。
 */
final class BewitchedPromotionQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(BewitchedPromotionQueue.class);
    private static final Set<UUID> PENDING = new LinkedHashSet<>();

    private BewitchedPromotionQueue() {
    }

    static void enqueue(UUID player) {
        PENDING.add(player);
    }

    static void remove(UUID player) {
        PENDING.remove(player);
    }

    static void clearAll() {
        PENDING.clear();
    }

    static void finishPromotions(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (UUID uuid : new ArrayList<>(PENDING)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            BewitchedRules.QueueAction action = decide(player);
            if (action == BewitchedRules.QueueAction.DEFER) {
                continue;
            }
            PENDING.remove(uuid);
            if (action == BewitchedRules.QueueAction.PROMOTE) {
                try {
                    BewitchedPromotionService.promote(player);
                } catch (RuntimeException exception) {
                    // Never retried every tick: a failing promotion is logged once and dropped. If the role never
                    // changed, the counter is rewound so the next task queues it again.
                    // 不会每刻重试：失败的晋升只记录一次日志并丢弃。若身份未变更，计数会回退，下一个任务会再次入队。
                    LOGGER.error("Bewitched promotion failed for {}", uuid, exception);
                    if (BewitchedRules.isBewitched(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
                        BewitchedPlayerComponent.KEY.get(player).rewindForRetry();
                    }
                }
            }
        }
    }

    private static BewitchedRules.QueueAction decide(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return BewitchedRules.queueAction(false, false, false, false, 0, false, false, false, false);
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return BewitchedRules.queueAction(
                true,
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player),
                // Real role, never a Black Raven acting overlay. / 真实身份，不读黑羽鸦伪装覆盖层。
                BewitchedRules.isBewitched(game.getRole(player)),
                BewitchedPlayerComponent.KEY.get(player).getPromotionTasks(),
                RiftSessionService.isInside(player),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                KidnapperControlComponent.KEY.get(player).isControlled(),
                isHunterAfflicted(HunterPlayerComponent.KEY.get(player)));
    }

    /** Rooted, fractured or carrying trap-poison credit; each ends on its own. / 定身、骨折或带有陷阱毒归属；均会自行结束。 */
    private static boolean isHunterAfflicted(HunterPlayerComponent hunter) {
        return hunter.isRooted() || hunter.getFractureLayers() > 0 || hunter.getTrapPoisonAttribution() != null;
    }
}
