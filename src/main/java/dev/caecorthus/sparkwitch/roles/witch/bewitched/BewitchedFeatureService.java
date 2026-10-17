package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Bewitched (魔化使) server registration, called once from SparkWitchEvents (the task listener itself sits next to the
 * Grand Witch's there). It is not an accomplice variant and owns no skill, so it never joins the special-accomplice
 * pool or the witch skill panel. Round start, round end and {@code ResetPlayer} clear the task counter and the queue;
 * round start also arms next-round accomplice-role locks, and round end drops the current round's.
 * 魔化使服务端注册，由 SparkWitchEvents 调用一次（任务监听器本身与大魔女的并列注册在那里）。它不是特殊共犯，也没有技能，
 * 因此从不加入特殊共犯池或魔女技能面板。开局、局末与 {@code ResetPlayer} 时清空任务计数与队列；开局时还会启用下一局共犯
 * 身份锁定，局末移除本局锁定。
 */
public final class BewitchedFeatureService {
    private static boolean registered;

    private BewitchedFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BewitchedShopService.register();
        ServerTickEvents.END_SERVER_TICK.register(BewitchedPromotionQueue::finishPromotions);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearRound(serverWorld);
                // Roles are dealt (forced Bewitched included): next-round locks apply to this round's Bewitched only
                // and lapse for everyone else, like Wathe's forced roles (owner, 2026-10-10). Locks live on the
                // overworld store (C5) even when the round runs in another dimension.
                // 身份已发放（含被强制的魔化使）：下一局锁定只对本局魔化使生效，其他人的作废，与 Wathe 强制身份相同
                // （所有者 2026-10-10）。即使对局在其他维度进行，锁定也保存在主世界存储上（C5）。
                WitchWorldComponent.KEY.get(serverWorld.getServer().getOverworld()).armPendingForcedAccompliceRoles(
                        uuid -> BewitchedPromotionService.isLivingBewitched(game, uuid));
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearRound(serverWorld);
                // Only this round's locks end with it; next-round locks wait for the next round start (they also
                // survive the stop Wathe runs on every world at server start). Overworld store (C5).
                // 只有本局锁定随本局结束；下一局锁定等待下一局开局（也能保留过服务器启动时 Wathe 对各世界的停止）。主世界存储（C5）。
                WitchWorldComponent.KEY.get(serverWorld.getServer().getOverworld()).clearForcedAccompliceRoles();
            }
        });
        ResetPlayer.EVENT.register(player -> {
            BewitchedPromotionQueue.remove(player.getUuid());
            BewitchedPlayerComponent.KEY.get(player).clear();
        });
    }

    private static void clearRound(ServerWorld world) {
        BewitchedPromotionQueue.clearAll();
        for (ServerPlayerEntity player : world.getPlayers()) {
            BewitchedPlayerComponent.KEY.get(player).clear();
        }
    }
}
