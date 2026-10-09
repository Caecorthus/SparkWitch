package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client crosshair hint for the AXMC at the hip: Wathe's target crosshair while a ready, chambered rifle points at a
 * visible player, as the revolver does. It only reads the world and the local player into
 * {@link UsecCrosshairRules}, which owns every rule. No role gate (the revolver hint has none, and the rifle never
 * checks the role); presentation only: the server decides every shot.
 * AXMC 腰射的客户端准星提示：就绪且已上膛的步枪指向可见玩家时显示 Wathe 的目标准星，与左轮相同。本类只把世界与本地玩家的
 * 状态读入 {@link UsecCrosshairRules}，所有规则都在那里。不按职业限制（左轮提示没有职业限制，步枪也从不检查职业）；仅用于
 * 展示：每一枪都由服务端判定。
 */
public final class UsecCrosshairTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");
    /** Search padding around the cut ray for puppets (their margin is far smaller). / 皮套搜索时截断射线周围的余量。 */
    private static final double PUPPET_SEARCH_PADDING = 1.0;

    private UsecCrosshairTargeting() {
    }

    /** Ready, chambered AXMC at the hip with a visible candidate on the ray. / 腰射位的就绪已上膛 AXMC 射线上有可见候选者。 */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        // Only the rifle answers; any other held item leaves the chained crosshair value untouched.
        // 只处理步枪；手持其他物品时不改动串联的准星值。
        if (!UsecRifleClient.isRifle(stack)) {
            return false;
        }
        UsecAmmoType chamber = UsecRifleState.read(stack).chamber();
        if (!UsecCrosshairRules.armed(true, player.getItemCooldownManager().isCoolingDown(stack.getItem()), chamber,
                UsecScopeProfile.isActive())) {
            return false;
        }
        Vec3d eye = player.getEyePos();
        double reach = UsecCrosshairRules.reach(chamber, SparkTraitsUsecBridge.marksmanRangeMultiplier(player));
        Vec3d end = UsecCrosshairRules.rayEnd(eye, UsecCrosshairRules.aim(player.getYaw(), player.getPitch()), reach);
        BlockHitResult block = player.getWorld().raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        return UsecCrosshairRules.entersAny(eye, end, candidateVolumes(player, eye, end));
    }

    private static List<Box> candidateVolumes(ClientPlayerEntity player, Vec3d eye, Vec3d end) {
        List<Box> volumes = new ArrayList<>();
        for (AbstractClientPlayerEntity candidate : player.clientWorld.getPlayers()) {
            if (UsecCrosshairRules.playerCandidate(candidate == player, candidate.isAlive(),
                    GameFunctions.isPlayerAliveAndSurvival(candidate), candidate.isInvisible(),
                    WraithClientState.isActive(candidate))) {
                volumes.add(UsecCrosshairRules.hitVolume(candidate.getBoundingBox()));
            }
        }
        for (MagicianPlaybackEntity puppet : player.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class,
                new Box(eye, end).expand(PUPPET_SEARCH_PADDING),
                candidate -> UsecCrosshairRules.puppetCandidate(candidate.isAlive(), candidate.isRemoved(),
                        candidate.isInvisible()))) {
            volumes.add(UsecCrosshairRules.hitVolume(puppet.getBoundingBox()));
        }
        return volumes;
    }
}
