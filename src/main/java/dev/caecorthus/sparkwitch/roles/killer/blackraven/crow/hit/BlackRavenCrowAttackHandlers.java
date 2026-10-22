package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe bat left-click on a crow: an {@code AttackEntityCallback} in the default phase, so the Seeker session lock, the
 * Control Expert stun and the Rift session guards (earlier phases) still refuse first. Fabric fires it before Wathe's
 * attack wrapper, which kills only player targets and drops a survival attack on any other entity, so the crow needs
 * this entry (the vanilla {@code damage()} bat branch of the crow is never reached in survival). The server kills
 * through {@link BlackRavenCrowHits#onBatHit} and answers SUCCESS; every other case PASSes (Mighty Force and the
 * Ceremonial Sword resolve the crow in their own handlers). The client always PASSes, so vanilla sends the attack packet
 * and resets the local attack charge exactly as for a player target.
 * 对乌鸦的 Wathe 球棒左键：默认阶段的 {@code AttackEntityCallback}，因此搜寻者会话锁、控场专家眩晕与裂隙会话守卫（更早的
 * 阶段）仍会先行拒绝。Fabric 在 Wathe 攻击包装之前触发它，而该包装只击杀玩家目标、并丢弃生存模式对其他实体的攻击，因此乌鸦需要
 * 本入口（生存模式下永远走不到乌鸦原版 {@code damage()} 的球棒分支）。服务端经 {@link BlackRavenCrowHits#onBatHit} 击杀并返回
 * SUCCESS；其余情况一律 PASS（巨力与仪礼剑在各自处理器中解析乌鸦）。客户端始终 PASS，因此原版与攻击玩家时完全一样发送攻击包并
 * 重置本地攻击蓄力。
 */
public final class BlackRavenCrowAttackHandlers {
    private static boolean registered;

    private BlackRavenCrowAttackHandlers() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AttackEntityCallback.EVENT.register(BlackRavenCrowAttackHandlers::onAttack);
    }

    static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                 @Nullable EntityHitResult hitResult) {
        if (world.isClient() || !(entity instanceof BlackRavenCrowEntity)
                || !(player instanceof ServerPlayerEntity attacker)) {
            return ActionResult.PASS;
        }
        return BlackRavenCrowHits.onBatHit(attacker, entity) ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
