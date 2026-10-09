package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkwitch.record.AchievementRecords;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-only writer of {@code sparkwitch:wind_spirit_fall} (achievement record contract W2). A promoted Wind Spirit's
 * wind charge that catches a living participant in its blast is noted in {@link WindSpiritKnockLedger}; a later
 * {@code wathe:fell_out_of_train} death inside the window records the event after Wathe's own death record. Record
 * only: the active Wraith's charge sets no attacker, and Wathe's fall credit (and SparkWitch's push credit) stay as
 * they are.
 * {@code sparkwitch:wind_spirit_fall}（成就记录契约 W2）的仅服务端写入方。晋升风精灵的风弹爆炸波及存活参赛者时记入
 * {@link WindSpiritKnockLedger}；窗口内随后发生的 {@code wathe:fell_out_of_train} 死亡会在 Wathe 自身死亡记录之后写入该事件。
 * 只做记录：激活冤魂的风弹不设置攻击者，Wathe 的坠车归属（以及 SparkWitch 的推人归属）保持原样。
 */
public final class WindSpiritFallRecords {
    private static final WindSpiritKnockLedger LEDGER = new WindSpiritKnockLedger();
    private static boolean registered;

    private WindSpiritFallRecords() {
    }

    /** Called once from {@link WindSpiritFeatureService#register()}. / 由 {@link WindSpiritFeatureService#register()} 调用一次。 */
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(WindSpiritFallRecords::afterKill);
        ResetPlayer.EVENT.register(player -> LEDGER.clearVictim(player.getUuid()));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> LEDGER.clearAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEDGER.clearAll());
    }

    /**
     * Vanilla {@code ServerPlayerEntity#onExplodedBy} hook: {@code source} is the exploding entity, a
     * {@link WindChargeEntity} for a wind charge. Only a promoted Wind Spirit's charge catching another living
     * participant is noted.
     * 原版 {@code ServerPlayerEntity#onExplodedBy} 钩子：{@code source} 为爆炸实体，风弹即 {@link WindChargeEntity}。
     * 只记录晋升风精灵的风弹波及其他存活参赛者的情况。
     */
    public static void onExplodedBy(ServerPlayerEntity victim, @Nullable Entity source) {
        if (!(source instanceof WindChargeEntity charge)
                || !(charge.getOwner() instanceof ServerPlayerEntity windSpirit)
                || windSpirit == victim
                || !WindSpiritRules.isActivePromotedWindSpirit(windSpirit)
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return;
        }
        LEDGER.knock(victim.getUuid(), windSpirit.getUuid(), victim.getServerWorld().getTime());
    }

    /** Any death consumes the victim's entry; only a train fall inside the window records. / 任何死亡都会消费记录；只有窗口内坠车才写入。 */
    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        UUID windSpirit = LEDGER.take(victim.getUuid(), victim.getServerWorld().getTime());
        if (windSpirit != null && GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            AchievementRecords.windSpiritFall(victim, windSpirit);
        }
    }
}
