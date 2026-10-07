package dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerFallAttributionService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssSuppression;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server half of the Shriek Gun: the fire decision, the match beam, the Seeker seam, the ally/enemy hit, the
 * fall-credit record, presentation and the replay record, plus the off-match presentation-only shot. Non-lethal: it
 * never kills, never sends {@code GunShootPayload} and never uses {@code wathe:gun_shot}; a push that drops someone off
 * the train is credited later by the shared fall ledger.
 * 啸音铳的服务端部分：开火判定、对局射线、搜寻者接缝、队友/敌人命中、坠车归因记录、表现与回放记录，以及对局外仅表现的
 * 射击。非致命：从不击杀、从不发送 {@code GunShootPayload}、从不使用 {@code wathe:gun_shot}；把人推下火车的击杀由
 * 共享坠车账本事后归属。
 */
public final class ShriekGunService {
    private static final SoundEvent FIRE_SOUND = SoundEvents.ENTITY_WARDEN_SONIC_BOOM;
    private static final float FIRE_VOLUME = 1.0F;
    private static final float FIRE_PITCH = 1.2F;
    /** Blindness has a single level. / 失明只有一个等级。 */
    private static final int BLINDNESS_AMPLIFIER = 0;

    private ShriekGunService() {
    }

    /**
     * Server fire decision; any holder may fire (owner rule 2026-10-04, {@link OffMatchUse}). The live test is a
     * playing, living, non-spectator, non-Wraith participant; the spectator, cooldown and SparkTraits weapon-action
     * checks apply to every mode. A refused use costs nothing.
     * 服务端开火判定；任何持有者都可开火（所有者 2026-10-04 规则，{@link OffMatchUse}）。存活判定为在局、存活、非旁观、
     * 非激活冤魂的参与者；旁观、冷却与 SparkTraits 武器动作检查对每种模式都生效。被拒绝的使用不产生任何代价。
     */
    static OffMatchUse.Mode fireMode(ServerPlayerEntity shooter, ItemStack gun) {
        return ShriekGunRules.fireMode(
                shooter.isSpectator(),
                OffMatchUse.mode(shooter, GameFunctions.isPlayerPlayingAndAlive(shooter) && !shooter.isSpectator()
                        && !WraithStateService.isActive(shooter)),
                () -> shooter.getItemCooldownManager().isCoolingDown(gun.getItem()),
                () -> SparkTraitsKillerBridge.blocksWeaponAction(shooter, gun));
    }

    /**
     * Off-match shot ({@link OffMatchUse.Mode#PRESENTATION}): the fire sound and the beam particles up to the block cut
     * of {@link ShriekGunTargeting#beamEnd}. It picks no target and touches no player, Seeker device, fall ledger or
     * replay; the caller still writes the cooldown.
     * 对局外射击（{@link OffMatchUse.Mode#PRESENTATION}）：开火音效与延伸到 {@link ShriekGunTargeting#beamEnd} 方块截断点的
     * 射线粒子。不选取目标，不触碰任何玩家、搜寻者设备、坠车账本或回放；冷却仍由调用方写入。
     */
    static void firePresentation(ServerPlayerEntity shooter, ServerWorld world) {
        Vec3d start = shooter.getEyePos();
        Vec3d end = ShriekGunTargeting.beamEnd(shooter, AbyssListenerRules.GUN_RANGE);
        spawnBeam(world, start, shooter.getRotationVec(1.0F), start.distanceTo(end));
        playFireSound(world, shooter);
    }

    /**
     * Match shot ({@link OffMatchUse.Mode#MATCH}): fires one beam; the caller then writes the vanilla cooldown, hit or
     * miss. Returns the player that was hit, or null.
     * 对局射击（{@link OffMatchUse.Mode#MATCH}）：发射一次射线；随后由调用方写入原版冷却（无论是否命中）。返回被命中的
     * 玩家或 null。
     */
    static @Nullable ServerPlayerEntity fire(ServerPlayerEntity shooter, ServerWorld world, Item gun) {
        Vec3d start = shooter.getEyePos();
        Vec3d direction = shooter.getRotationVec(1.0F);
        Vec3d end = ShriekGunTargeting.beamEnd(shooter, AbyssListenerRules.GUN_RANGE);
        // Players the Abyss Listener may not affect are filtered before the pick, so they are transparent. The shooter
        // aimed at its delayed client view, so the server tests rewound volumes.
        // 聆渊者无法影响的玩家在选取之前就被过滤，因此对射线透明。射手瞄准的是客户端延迟画面，因此服务端检测回溯后的体积。
        ShriekGunTargeting.Hit<ServerPlayerEntity> hit = ShriekGunTargeting.firstHit(shooter, start, end,
                world.getPlayers(), candidate -> isEligible(shooter, candidate),
                candidate -> PlayerHitboxHistory.hitVolumes(shooter, candidate, ShriekGunTargeting.BOX_EXPANSION));
        ServerPlayerEntity picked = hit == null ? null : hit.target();
        // Read-only snapshot with the break's own ray and breakability filter, so the beam particles stop exactly where
        // a device absorbed it. / 只读快照，使用与打坏相同的射线与可打坏过滤，使粒子恰好停在吸收射线的设备处。
        SeekerDeviceEntity blocker = SeekerDeviceHits.shriekGunAbsorber(shooter, picked, AbyssListenerRules.GUN_RANGE);
        // Seeker seam: a nearer Seeker device absorbs the beam and breaks; nobody is pushed (replay hit:false).
        // 搜寻者接缝：更近的搜寻者设备吸收射线并被打坏；不推动任何人（回放 hit:false）。
        ServerPlayerEntity target = SeekerDeviceHits.onShriekGunFired(shooter, picked, AbyssListenerRules.GUN_RANGE);

        double cut = start.distanceTo(end);
        boolean ally = false;
        if (target != null) {
            cut = Math.min(cut, Math.sqrt(hit.distanceSquared()));
            ally = applyHit(shooter, target, direction, gun);
        } else if (blocker != null && blocker.isRemoved()) {
            cut = Math.min(cut, deviceDistance(start, end, blocker));
        }
        spawnBeam(world, start, direction, cut);
        playFireSound(world, shooter);
        NbtCompound extra = new NbtCompound();
        extra.putBoolean("hit", target != null);
        if (target != null) {
            extra.putBoolean("ally", ally);
        }
        GameRecordManager.recordItemUse(shooter, AbyssListenerRules.GUN_ITEM_ID, target, extra);
        return target;
    }

    /**
     * Eligibility before geometry: the shared Abyss suppression gate (participant, SFA veto, Last Stand / Last Escape;
     * allies stay eligible because they get the ally branch) plus Vendetta pair isolation.
     * 资格先于几何：共享的聆渊压制门槛（参与者、SFA 否决、背水一战/绝处逢生；队友仍然合格，因为他们走队友分支）
     * 加上复仇者配对隔离。
     */
    static boolean isEligible(ServerPlayerEntity shooter, ServerPlayerEntity candidate) {
        return AbyssSuppression.canAffect(shooter, candidate, AbyssListenerRules.GUN_ACTION_ID)
                && ShriekGunRules.vendettaAllows(
                VendettaInteractionService.isActiveVendetta(shooter),
                VendettaInteractionService.isActiveVendetta(candidate),
                VendettaInteractionService.isExactPair(shooter, candidate));
    }

    /**
     * Pushes {@code target} and applies its branch. Every push, ally or enemy, is recorded for train-fall credit
     * (owner D12): a teammate launched off the train is the Abyss Listener's kill too, with its coins and mana.
     * Returns whether the target was an ally.
     * 推动目标并施加其分支效果。每次推击（队友或敌人）都记录用于坠车归因（作者决定 D12）：把队友推下火车同样算作
     * 聆渊者的击杀，照常获得金币与魔力。返回目标是否为队友。
     */
    private static boolean applyHit(ServerPlayerEntity shooter, ServerPlayerEntity target, Vec3d direction, Item gun) {
        boolean ally = AbyssSuppression.isAlly(target);
        ShriekGunRules.HitPlan plan = ShriekGunRules.planFor(ally, !ally && AbyssSuppression.hasRealSanity(target));
        Vec3d velocity = ShriekGunRules.knockback(direction, target.getPos().subtract(shooter.getPos()),
                target.getVelocity().y, plan.horizontalSpeed(), plan.lift());
        // Absolute velocity, flushed to the target and its trackers at the end of this tick (sync-capped per axis).
        // 绝对速度，在本刻末尾同步给目标及其追踪者（每轴受同步上限约束）。
        target.setVelocity(velocity);
        target.velocityModified = true;
        FirePokerFallAttributionService.recordPush(shooter, target, gun);
        if (plan.suppress()) {
            AbyssSuppression.drainSanity(target, AbyssListenerRules.GUN_SANITY_LOSS);
            AbyssSuppression.addEffect(target, StatusEffects.SLOWNESS, AbyssListenerRules.GUN_DEBUFF_TICKS,
                    AbyssListenerRules.GUN_SLOWNESS_AMPLIFIER, shooter);
            AbyssSuppression.addEffect(target, StatusEffects.BLINDNESS, AbyssListenerRules.GUN_DEBUFF_TICKS,
                    BLINDNESS_AMPLIFIER, shooter);
            AbyssSuppression.forceCooldowns(target, AbyssListenerRules.GUN_FORCED_COOLDOWN_TICKS);
        }
        if (plan.allySpeed()) {
            AbyssSuppression.addEffect(target, StatusEffects.SPEED, AbyssListenerRules.GUN_ALLY_SPEED_TICKS,
                    AbyssListenerRules.GUN_ALLY_SPEED_AMPLIFIER, shooter);
        }
        return plan.ally();
    }

    private static void spawnBeam(ServerWorld world, Vec3d start, Vec3d direction, double cut) {
        Vec3d unit = direction.normalize();
        for (double distance : ShriekGunRules.particleDistances(cut)) {
            Vec3d point = start.add(unit.multiply(distance));
            world.spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void playFireSound(ServerWorld world, ServerPlayerEntity shooter) {
        world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), FIRE_SOUND, SoundCategory.PLAYERS,
                FIRE_VOLUME, FIRE_PITCH);
    }

    private static double deviceDistance(Vec3d start, Vec3d end, SeekerDeviceEntity device) {
        double squared = HitscanLagRules.entryDistanceSquared(start, end, List.of(device.getBoundingBox()));
        return squared >= 0.0 ? Math.sqrt(squared) : start.distanceTo(device.getBoundingBox().getCenter());
    }
}
