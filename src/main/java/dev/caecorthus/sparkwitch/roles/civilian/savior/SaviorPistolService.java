package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.SaviorGunDropS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorMuzzleS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorPistolShootC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowTargets;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotGuns;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent.ShootInnocentPunishment;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Server half of the Savior's pistol, copied from Wathe's {@code GunShootPayload.Receiver}. It keeps the revolver's
 * click/shoot sounds, 4° recoil feedback, muzzle packet, 65-block target tolerance, innocent-shooting punishment and
 * backfire, but drops the two civilian-pistol modifiers the owner excluded: the innocent gun cooldown override and the
 * -0.35 mood cost of a kill (the Savior is police). The 10 s cooldown is always written, hit or miss, except in
 * creative mode. While a game runs any living holder may fire ({@link SaviorRules#mayUseWeapons}).
 * 救世主手枪的服务端部分，复制自 Wathe 的 {@code GunShootPayload.Receiver}。保留左轮的点击/开枪音效、4° 后坐反馈、
 * 枪口数据包、65 格目标容差、误杀惩罚与后坐反噬，但去掉所有者排除的两项平民手枪修正：误杀冷却覆盖与击杀的 -0.35 理智
 * 损失（救世主属于警类）。无论命中与否都写入 10 秒冷却，创造模式除外。对局进行中任何存活的持有者都能开枪
 * （{@link SaviorRules#mayUseWeapons}）。
 */
public final class SaviorPistolService {
    private static boolean registered;

    private SaviorPistolService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(SaviorPistolShootC2SPayload.ID, SaviorPistolShootC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SaviorMuzzleS2CPayload.ID, SaviorMuzzleS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SaviorGunDropS2CPayload.ID, SaviorGunDropS2CPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaviorPistolShootC2SPayload.ID,
                (payload, context) -> shoot(context.player(), payload.targetEntityId()));
    }

    private static void shoot(ServerPlayerEntity shooter, int targetEntityId) {
        if (shooter == null || shooter.isSpectator()) {
            return;
        }
        ItemStack pistol = shooter.getMainHandStack();
        // Creative mode may fire even if an old cooldown is still ticking. / 创造模式即使旧冷却仍在也能开枪。
        boolean creative = shooter.isCreative();
        if (!pistol.isOf(SparkWitchItems.saviorPistol())
                || (!creative && shooter.getItemCooldownManager().isCoolingDown(pistol.getItem()))
                || !SaviorRules.mayUseWeapons(shooter)
                || refusesShooter(shooter, pistol)) {
            return;
        }
        ServerWorld world = shooter.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);

        // The client sends whatever RevolverItem.getGunTarget picked. Raven Form seam, as in Wathe's revolver lookup: a
        // crow on the shooter's look ray resolves to its owner, so the whole player path below runs on the Black Raven.
        // 客户端发送 RevolverItem.getGunTarget 选中的任意实体。化鸦接缝，与 Wathe 左轮查找一致：射手视线上的乌鸦解析为其
        // 主人，下方整条玩家路径都作用于黑羽鸦。
        Entity resolved = BlackRavenCrowHits.onGunTargetLookup(shooter, world.getEntityById(targetEntityId));
        // The server only accepts a real player within the revolver's lag tolerance; the aim itself is the client's. A
        // substituted owner was already measured on its crow. / 服务端只接受真实玩家且在左轮的延迟容差内；瞄准本身由客户端
        // 决定。被替身的主人已按乌鸦量过距离。
        ServerPlayerEntity target = null;
        if (resolved instanceof ServerPlayerEntity candidate
                && !candidate.isSpectator()
                && BlackRavenCrowHits.receiverDistance(candidate, shooter.distanceTo(candidate))
                < SaviorRules.PISTOL_SERVER_TOLERANCE) {
            target = candidate;
        }
        // SparkFactionAPI drops a protected wathe:gunshoot at HEAD (no click, no cooldown); the pistol does the same.
        // SparkFactionAPI 在 HEAD 丢弃受保护的 wathe:gunshoot（无点击声、无冷却）；手枪同样处理。
        if (target != null && !SaviorRules.mayAffect(shooter, target, GameConstants.DeathReasons.GUN,
                SaviorRules.PISTOL_DEATH_REASON, game)) {
            BlackRavenCrowTargets.forget(target);
            return;
        }

        // Wathe's click plays before the shot, even when nothing is hit. / Wathe 的点击声先于枪声，未命中也播放。
        world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SparkWitchSounds.SAVIOR_PISTOL_CLICK, SoundCategory.PLAYERS, 0.5F, pitchVariation(shooter));
        if (target == null) {
            // Revolver parity for non-player ids: a puppet, a Seeker device or a Partygoer takes the hit, and the shot
            // still finishes as a recorded miss (sounds, muzzle, cooldown; no punishment, no mood).
            // 非玩家 id 与左轮一致：皮套、搜寻者设备或派对客承受命中，这一枪仍按记录的未命中收尾（音效、枪口、冷却；无惩罚、
            // 不扣理智）。
            hitWorldTarget(shooter, resolved, pistol);
        }
        // Record the shot exactly like Wathe's revolver instead of the custom pistol id. / 按 Wathe 左轮的身份记录，不用自定义手枪 id。
        GameRecordManager.recordItemUse(shooter, Registries.ITEM.getId(WatheItems.REVOLVER), target, null);

        boolean backfired = false;
        if (target != null) {
            ShouldPunishGunShooter.PunishResult punishment =
                    ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, target);
            if (punishment != null && punishment.hasCustomPunishment()) {
                Scheduler.schedule(punishment::executeCustomPunishment, SaviorRules.PUNISH_DELAY_TICKS);
            } else if (SaviorRules.isGoodPlayer(target, game) && !shooter.isCreative()
                    && (punishment == null || punishment.shouldPunish())) {
                ShootInnocentPunishment policy = game.getShootInnocentPunishment();
                if (SaviorRules.isGoodPlayer(shooter, game)
                        && shooter.getRandom().nextFloat() <= game.getBackfireChance()) {
                    // The gun backfires before the bullet leaves: the target survives. / 炸膛先于子弹离膛：目标存活。
                    backfired = true;
                    GameFunctions.killPlayer(shooter, true, shooter, GameConstants.DeathReasons.GUN_BACKFIRE);
                } else {
                    Scheduler.schedule(() -> punish(shooter, game, policy), SaviorRules.PUNISH_DELAY_TICKS);
                }
            }
            if (!backfired) {
                SaviorRules.settleKill(target, shooter, SaviorRules.PISTOL_DEATH_REASON);
            }
            // A backfire leaves a crow stand-in armed; disarm it. / 炸膛会留下已装好的乌鸦替身，这里解除。
            BlackRavenCrowTargets.forget(target);
        }

        // Copied volume 5.0: the revolver shot carries far. / 复制自左轮的 5.0 音量：枪声传得很远。
        world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SparkWitchSounds.SAVIOR_PISTOL_SHOOT, SoundCategory.PLAYERS, 5.0F, pitchVariation(shooter));
        SaviorMuzzleS2CPayload muzzle = new SaviorMuzzleS2CPayload(shooter.getUuidAsString());
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(shooter)) {
            ServerPlayNetworking.send(viewer, muzzle);
        }
        ServerPlayNetworking.send(shooter, muzzle);
        if (!shooter.isSpectator() && !creative) {
            // 10 s after every shot; a kill grants no extra cooldown. Creative mode never enters cooldown.
            // 每次射击固定 10 秒；击杀不额外增加冷却。创造模式不进入冷却。
            SaviorCooldowns.writeCooldown(shooter, pistol.getItem(), SaviorRules.PISTOL_COOLDOWN_TICKS);
        }
    }

    /**
     * Shooter gates of the other SparkWitch fire services (USEC rifle, M32A1), checked before the click: the
     * SparkTraits weapon gate, the Control Expert stun, the Seeker remote lock, Kidnapper control, an active Wraith,
     * and the Dominator rule that refuses a hand shot during a forced-shot sequence.
     * 与其他 SparkWitch 开火服务（USEC 步枪、M32A1）相同的射手门槛，在点击声之前检查：SparkTraits 武器门槛、控场专家眩晕、
     * 搜寻者远程锁定、绑匪操控、激活的冤魂，以及支配者强制开枪序列中拒绝亲手开枪的规则。
     */
    private static boolean refusesShooter(ServerPlayerEntity shooter, ItemStack pistol) {
        return SparkTraitsKillerBridge.blocksWeaponAction(shooter, pistol)
                || ControlExpertStun.isStunned(shooter)
                || SeekerRemoteSessionService.isLocked(shooter)
                || isKidnapped(shooter)
                || WraithStateService.isActive(shooter)
                || DominatorForcedShotGuns.refusesHandShot(shooter);
    }

    private static boolean isKidnapped(ServerPlayerEntity player) {
        return KidnapperControlComponent.KEY.maybeGet(player).map(KidnapperControlComponent::isControlled)
                .orElse(false);
    }

    /**
     * Non-player target, after the click and before the record, through the add-on gun entries
     * ({@code SparkWitchApi.hitGunWorldTarget} refuses items outside {@code wathe:guns}, so they are called directly):
     * a Magician puppet ends (decoy, the Magician is paid), a Seeker device breaks, and a SparkStrength Partygoer dies
     * (owner rule 2026-10-21). Each re-checks aim and line of sight within the revolver's 65-block cap. A Bomber drone
     * has no SparkWitch seam and stays a miss.
     * 非玩家目标，在点击声之后、记录之前，经附属枪械入口处理（{@code SparkWitchApi.hitGunWorldTarget} 拒绝
     * {@code wathe:guns} 以外的物品，因此直接调用）：魔术师皮套被结束（诱饵，魔术师获得报酬），搜寻者设备被打坏，
     * SparkStrength 派对客死亡（所有者规则 2026-10-21）。每项都在左轮 65 格上限内复查瞄准与视线。轰炸者无人机没有
     * SparkWitch 接缝，仍为未命中。
     */
    private static boolean hitWorldTarget(ServerPlayerEntity shooter, @Nullable Entity entity, ItemStack pistol) {
        if (entity == null || entity instanceof PlayerEntity) {
            return false;
        }
        double max = SaviorRules.PISTOL_SERVER_TOLERANCE;
        return MagicianPuppetHits.onAddonGunShot(shooter, entity, pistol, max)
                || SeekerDeviceHits.onAddonGunShot(shooter, entity, max)
                || killPartygoer(shooter, entity, max);
    }

    private static boolean killPartygoer(ServerPlayerEntity shooter, Entity entity, double max) {
        if (!SparkStrengthPartygoerCompat.isKillable(entity) || entity.getWorld() != shooter.getWorld()
                || shooter.distanceTo(entity) >= max) {
            return false;
        }
        Box box = entity.getBoundingBox();
        if (!SeekerDamageRules.gunAimedAndVisible(shooter.getWorld(), shooter.getEyePos(),
                shooter.getRotationVec(1.0F), max, box, box.expand(entity.getTargetingMargin()), shooter)) {
            return false;
        }
        return SparkStrengthPartygoerCompat.killByWeapon(shooter, entity, SaviorRules.PISTOL_DEATH_REASON);
    }

    /**
     * Wathe's innocent-shooting punishment, four ticks after the shot and only on a shooter still alive in the round
     * (any holder): take the pistol; when the shooter cannot use killer features, drop a Savior's own hidden revolver
     * (Wathe removes one gun and drops one, so the count stays constant), clear mood, block picking guns back up, and
     * under {@code KILL_SHOOTER} also kill the shooter.
     * Wathe 的误杀惩罚，在开枪四刻后执行，且只作用于仍在对局中存活的射手（任何持有者）：收走手枪；当射手不能使用杀手功能时
     * 掉落救世主自己的隐藏左轮（Wathe 收走一把、掉落一把，枪支数量不变）、清空理智、禁止再拾枪，并在 {@code KILL_SHOOTER}
     * 策略下击杀射手。
     */
    private static void punish(ServerPlayerEntity shooter, GameWorldComponent game, ShootInnocentPunishment policy) {
        if (!SaviorRules.isPunishable(shooter)
                || !SaviorRules.takeOne(shooter.getInventory(), SparkWitchItems.saviorPistol())) {
            return;
        }
        if (game.canUseKillerFeatures(shooter)) {
            return;
        }
        SaviorRules.dropHiddenRevolver(shooter);
        ServerPlayNetworking.send(shooter, new SaviorGunDropS2CPayload());
        PlayerMoodComponent.KEY.get(shooter).setMood(0.0F);
        game.addToPreventGunPickup(shooter);
        if (SaviorRules.isGoodPlayer(shooter, game) && policy == ShootInnocentPunishment.KILL_SHOOTER) {
            GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    private static float pitchVariation(ServerPlayerEntity player) {
        return 1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F;
    }
}
