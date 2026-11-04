package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.compat.SparkTraitsControlExpertBridge;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.RevolverItem;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;

/**
 * Server orchestration of the forced shot: begin the swap/protection sequence, fire once through Wathe's own gun
 * receiver while suppressing kill money, and restore the original stack when the sequence ends. If the shot kills a
 * player, the Dominator is paid instead of the forced shooter.
 * 强制开枪的服务端编排：开始换枪/保护序列，在压制击杀奖金的情况下通过 Wathe 原本的枪械接收器开一枪，
 * 序列结束时还原原物品。若这一枪打死了玩家，则奖金发给支配者而不是被强制开枪者。
 */
public final class DominatorForcedShotService {
    private DominatorForcedShotService() {
    }

    public static boolean begin(ServerPlayerEntity marked) {
        return DominatorForcedShotComponent.KEY.get(marked).begin();
    }

    public static void fireNow(ServerPlayerEntity shooter, ServerPlayerEntity dominator) {
        // Same player-only ray as Wathe's 30-block revolver, extended by an active range trait (Marksman).
        // 与 Wathe 左轮相同的仅玩家射线；若射手有生效的射程词条（精确枪手）则按倍率延长。
        double range = DominatorRules.FORCED_SHOT_RANGE
                * SparkTraitsControlExpertBridge.marksmanRangeMultiplier(shooter);
        HitResult hit = ProjectileUtil.getCollision(
                shooter,
                entity -> entity instanceof PlayerEntity player && GameFunctions.isPlayerAliveAndSurvival(player),
                range
        );
        int targetId = RevolverItem.resolveTargetFromHitResult(shooter.getWorld(), hit);
        Entity aimed = shooter.getWorld().getEntityById(targetId);
        PlayerEntity victim = aimed instanceof PlayerEntity player && GameFunctions.isPlayerPlayingAndAlive(player)
                ? player
                : null;
        DominatorForcedShotRewards.runSuppressed(() -> {
            ServerPlayNetworking.Context context = new ForcedGunContext(
                    shooter.getServer(),
                    shooter,
                    ServerPlayNetworking.getSender(shooter)
            );
            new GunShootPayload.Receiver().receive(new GunShootPayload(targetId), context);
        });
        // The receiver resolves kills synchronously, so a victim alive before the shot and dead after it was killed by
        // this shot. Pay outside the suppression window; the reward belongs to the Dominator, not the forced shooter.
        // 接收器同步处理击杀：开枪前存活、开枪后死亡的目标即为这一枪所杀。在压制窗口外发奖金，奖金归支配者而非开枪者。
        if (victim != null
                && dominator != victim
                && !GameFunctions.isPlayerPlayingAndAlive(victim)
                && GameFunctions.isPlayerPlayingAndAlive(dominator)) {
            PlayerShopComponent.KEY.get(dominator).addToBalance(DominatorRules.FORCED_SHOT_KILL_REWARD);
        }
    }

    public static void restoreAndClear(ServerPlayerEntity player) {
        DominatorForcedShotComponent.KEY.get(player).restoreNow();
    }

    private record ForcedGunContext(
            MinecraftServer server,
            ServerPlayerEntity player,
            PacketSender responseSender
    ) implements ServerPlayNetworking.Context {
    }
}
