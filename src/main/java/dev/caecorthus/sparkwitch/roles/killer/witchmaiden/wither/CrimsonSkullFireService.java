package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net.FireCrimsonSkullC2SPayload;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastGeometry;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Wither Gohei left-click: the summoned Mini Wither fires a crimson skull at the point under the crosshair, for 75 mana
 * and a 15 s Gohei cooldown (owner Q4/Q7). The skull leaves the Wither, not the eye, so a target close to the owner is
 * aimed at directly.
 * 凋灵御币左键：已召唤的小凋灵朝准星下的点射出赤色凋灵之首，花费 75 魔力，御币冷却 15 秒（所有者 Q4/Q7）。头颅从小凋灵而非
 * 眼睛射出，因此靠近主人的目标会被直接瞄准。
 */
public final class CrimsonSkullFireService {
    static final String NO_WITHER_KEY = "message.sparkwitch.mini_wither.not_summoned";
    static final String NOT_ENOUGH_MANA_KEY = "message.sparkwitch.mini_wither.not_enough_mana";
    private static final float LAUNCH_VOLUME = 1.0F;
    private static final float LAUNCH_PITCH = 0.75F;

    private CrimsonSkullFireService() {
    }

    public static void fire(ServerPlayerEntity player, FireCrimsonSkullC2SPayload payload) {
        if (player == null || payload == null || OffMatchUse.mode(player) != OffMatchUse.Mode.MATCH
                || player.isSpectator() || ControlExpertStun.isStunned(player)
                || SeekerRemoteSessionService.isLocked(player)) {
            return;
        }
        ItemStack gohei = player.getMainHandStack();
        if (!WitherGoheiInventoryRules.isGohei(gohei)
                || !WitchMaidenRules.isWitchMaiden(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || SparkTraitsKillerBridge.blocksWeaponAction(player, gohei)
                || player.getItemCooldownManager().isCoolingDown(gohei.getItem())) {
            return;
        }
        if (!MiniWitherPlayerComponent.KEY.get(player).isActive()) {
            player.sendMessage(Text.translatable(NO_WITHER_KEY).withColor(WitchMaidenRules.COLOR), true);
            return;
        }
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        if (!mana.hasManaSystem() || mana.getMana() < MiniWitherRules.CRIMSON_MANA_COST) {
            player.sendMessage(Text.translatable(NOT_ENOUGH_MANA_KEY, MiniWitherRules.CRIMSON_MANA_COST)
                    .withColor(WitchMaidenRules.COLOR), true);
            return;
        }
        float yaw = payload.hasAim() ? MathHelper.wrapDegrees(payload.yaw()) : player.getYaw();
        float pitch = payload.hasAim() ? MathHelper.clamp(payload.pitch(), -90.0F, 90.0F) : player.getPitch();
        Vec3d anchor = MiniWitherAnchor.position(player);
        Vec3d aim = aimPoint(player, yaw, pitch);
        if (!MiniWitherSkullEntity.launch(player, MiniWitherSkullEntity.Kind.CRIMSON, MiniWitherRules.Mode.HOSTILE,
                anchor, aim)) {
            return;
        }
        Vec3d shot = aim.subtract(anchor);
        MiniWitherPlayerComponent.KEY.get(player).setAim(MiniWitherRules.yawOf(shot), MiniWitherRules.pitchOf(shot));
        mana.spendMana(MiniWitherRules.CRIMSON_MANA_COST);
        player.getItemCooldownManager().set(gohei.getItem(), MiniWitherRules.CRIMSON_COOLDOWN_TICKS);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS,
                LAUNCH_VOLUME, LAUNCH_PITCH);
    }

    /**
     * What the crosshair rests on within 48 blocks: the first living player, else the first block, else the end of the
     * range. The click aimed at the owner's delayed client view, so players are tested against their lag-compensated
     * volumes ({@link PlayerHitboxHistory#hitVolumes}); the skull then aims at that player's current box
     * ({@link MiniWitherRules#crimsonAimOnTarget}), or a moving target would be missed and the skull would burst on
     * the wall behind it (fix 2026-10-25).
     * 准星在 48 格内落在的位置：第一名存活玩家，否则第一个方块，否则射程末端。点击时主人瞄准的是客户端的延迟画面，因此按玩家的
     * 延迟补偿体积（{@link PlayerHitboxHistory#hitVolumes}）检测；头颅随后瞄准该玩家的当前箱体
     * （{@link MiniWitherRules#crimsonAimOnTarget}），否则移动中的目标会被打空，头颅在其身后的墙上炸开（2026-10-25 修复）。
     */
    static Vec3d aimPoint(ServerPlayerEntity player, float yaw, float pitch) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(PotionBlastGeometry.direction(yaw, pitch).multiply(MiniWitherRules.CRIMSON_RANGE));
        HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        ServerPlayerEntity target = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity candidate : player.getServerWorld().getPlayers()) {
            if (!isAimable(player, candidate)) {
                continue;
            }
            double entry = HitscanLagRules.entryDistanceSquared(eye, end,
                    PlayerHitboxHistory.hitVolumes(player, candidate, MiniWitherRules.CRIMSON_AIM_MARGIN));
            if (entry >= 0.0 && entry < nearest) {
                target = candidate;
                nearest = entry;
            }
        }
        return target == null ? end
                : MiniWitherRules.crimsonAimOnTarget(eye, end, Math.sqrt(nearest), target.getBoundingBox());
    }

    private static boolean isAimable(ServerPlayerEntity owner, ServerPlayerEntity candidate) {
        return candidate != owner && GameFunctions.isPlayerAliveAndSurvival(candidate)
                && GameFunctions.isPlayerPlayingAndAlive(candidate);
    }
}
