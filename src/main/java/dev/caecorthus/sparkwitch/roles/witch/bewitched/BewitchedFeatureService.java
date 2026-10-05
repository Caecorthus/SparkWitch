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
 * pool or the witch skill panel. Round start, round end and {@code ResetPlayer} clear the task counter and the queue.
 * 魔化使服务端注册，由 SparkWitchEvents 调用一次（任务监听器本身与大魔女的并列注册在那里）。它不是特殊共犯，也没有技能，
 * 因此从不加入特殊共犯池或魔女技能面板。开局、局末与 {@code ResetPlayer} 时清空任务计数与队列。
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
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearRound(serverWorld);
                // Locks live on the overworld store (C5); clear them even when the round ran in another dimension.
                // 锁定保存在主世界存储上（C5）；即使对局在其他维度进行也要清空。
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
