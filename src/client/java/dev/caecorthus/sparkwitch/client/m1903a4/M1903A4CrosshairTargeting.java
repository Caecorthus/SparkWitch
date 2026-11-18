package dev.caecorthus.sparkwitch.client.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Combat;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Rules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client crosshair hint for the M1903A4: Wathe's target crosshair while the rifle is being charged in the main hand and
 * the nearest candidate on its block-clipped {@link M1903A4Combat#RANGE} ray is a visible living player (or a live
 * Magician puppet as its copied player) that BEAR smoke does not hide. It only reads the world and the local player
 * into {@link M1903A4CrosshairRules}; presentation only, the server resolves every shot on its own and never asks this.
 * M1903A4 的客户端准星提示：主手蓄力时，若按方块截断的 {@link M1903A4Combat#RANGE} 射线上最近的候选者是未被 BEAR 烟雾
 * 遮挡的可见存活玩家（或视同其复制玩家的存活魔术师皮套），显示 Wathe 的目标准星。本类只把世界与本地玩家的状态读入
 * {@link M1903A4CrosshairRules}；仅用于展示，服务端独立结算每一枪，从不调用本类。
 */
public final class M1903A4CrosshairTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");
    /** Search padding around the cut ray for puppets. / 皮套搜索时截断射线周围的余量。 */
    private static final double PUPPET_SEARCH_PADDING = 1.0;

    private M1903A4CrosshairTargeting() {
    }

    /** Charging rifle with a visible nearest candidate on the ray. / 正在蓄力的步枪射线上最近的候选者可见。 */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        // Only the rifle answers; any other held item leaves the chained crosshair value untouched.
        // 只处理步枪；手持其他物品时不改动串联的准星值。
        if (!M1903A4CrosshairRules.charging(stack.isOf(SparkWitchItems.m1903a4()),
                player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND,
                player.getItemCooldownManager().isCoolingDown(stack.getItem()))) {
            return false;
        }
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(M1903A4Combat.RANGE));
        BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        // Boxes grow by the server shot's margin, so the hint and the shot agree on the same edge.
        // 箱体按服务端射击的余量扩大，使提示与射击对边缘的判定一致。
        Entity target = M1903A4CrosshairRules.nearest(eye, end, candidates(player, eye, end),
                entity -> entity.getBoundingBox().expand(M1903A4Rules.HIT_MARGIN));
        // The round stops at the nearest candidate, so one hidden by BEAR smoke keeps the hint dark (hint only).
        // 子弹停在最近的候选者处，因此被 BEAR 烟雾挡住的候选者让提示保持熄灭（仅提示）。
        return target != null && !SmokeTargetOcclusion.hides(player, target);
    }

    private static List<Entity> candidates(ClientPlayerEntity player, Vec3d eye, Vec3d end) {
        List<Entity> candidates = new ArrayList<>();
        for (AbstractClientPlayerEntity candidate : player.clientWorld.getPlayers()) {
            if (M1903A4CrosshairRules.playerCandidate(candidate == player, candidate.isAlive(),
                    GameFunctions.isPlayerAliveAndSurvival(candidate), candidate.isInvisible(),
                    WraithClientState.isActive(candidate))) {
                candidates.add(candidate);
            }
        }
        candidates.addAll(player.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class,
                new Box(eye, end).expand(PUPPET_SEARCH_PADDING),
                puppet -> M1903A4CrosshairRules.puppetCandidate(puppet.isAlive(), puppet.isRemoved(),
                        puppet.isInvisible())));
        return candidates;
    }
}
