package dev.caecorthus.sparkwitch.roles.killer.magician;

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
 * Wathe bat left-click on a Magician puppet: an {@code AttackEntityCallback} in the default phase, so the Seeker session
 * lock, the Control Expert stun and the Rift session guards (earlier phases) still refuse first. Fabric fires it at
 * {@code ServerPlayerEntity#attack} HEAD, before Wathe's own attack wrapper, which kills only player targets and
 * otherwise drops a non-creative attack on any other entity. The server ends a puppet through
 * {@link MagicianPuppetHits#onBatHit} and answers SUCCESS (the attack is consumed); every other case PASSes, so a
 * bare-hand, other-item or uncharged left-click on a puppet still does nothing. The client always PASSes: vanilla then
 * sends the attack packet and resets the local attack charge exactly as for a player target.
 * 对魔术师皮套的 Wathe 球棒左键：默认阶段的 {@code AttackEntityCallback}，因此搜寻者会话锁、控场专家眩晕与裂隙会话守卫
 * （更早的阶段）仍会先行拒绝。Fabric 在 {@code ServerPlayerEntity#attack} HEAD 触发它，早于 Wathe 自身的攻击包装（后者只击杀
 * 玩家目标，否则对其他实体的非创造攻击直接丢弃）。服务端经 {@link MagicianPuppetHits#onBatHit} 结束皮套并返回 SUCCESS
 * （攻击被消费）；其余情况一律 PASS，因此空手、其他物品或未蓄满的左键打在皮套上仍然毫无效果。客户端始终 PASS：原版随后
 * 与攻击玩家时完全一样发送攻击包并重置本地攻击蓄力。
 */
public final class MagicianPuppetAttackHandlers {
    private static boolean registered;

    private MagicianPuppetAttackHandlers() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AttackEntityCallback.EVENT.register(MagicianPuppetAttackHandlers::onAttack);
    }

    static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                 @Nullable EntityHitResult hitResult) {
        if (world.isClient() || !(entity instanceof MagicianPlaybackEntity)
                || !(player instanceof ServerPlayerEntity attacker)) {
            return ActionResult.PASS;
        }
        return MagicianPuppetHits.onBatHit(attacker, entity) ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
