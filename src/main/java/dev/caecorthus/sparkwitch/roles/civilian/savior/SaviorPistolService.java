package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.net.SaviorGunDropS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorMuzzleS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorPistolShootC2SPayload;
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
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

/**
 * Server half of the Savior's pistol, copied from Wathe's {@code GunShootPayload.Receiver}. It keeps the revolver's
 * click/shoot sounds, 4° recoil feedback, muzzle packet, 65-block target tolerance, innocent-shooting punishment and
 * backfire, but drops the two civilian-pistol modifiers the owner excluded: the innocent gun cooldown override and the
 * -0.35 mood cost of a kill (the Savior is police). The 10 s cooldown is always written, hit or miss, except in
 * creative mode.
 * 救世主手枪的服务端部分，复制自 Wathe 的 {@code GunShootPayload.Receiver}。保留左轮的点击/开枪音效、4° 后坐反馈、
 * 枪口数据包、65 格目标容差、误杀惩罚与后坐反噬，但去掉所有者排除的两项平民手枪修正：误杀冷却覆盖与击杀的 -0.35 理智
 * 损失（救世主属于警类）。无论命中与否都写入 10 秒冷却，创造模式除外。
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
                || (!creative && shooter.getItemCooldownManager().isCoolingDown(pistol.getItem()))) {
            return;
        }
        ServerWorld world = shooter.getServerWorld();
        // Wathe's click plays before the shot, even when nothing is hit. / Wathe 的点击声先于枪声，未命中也播放。
        world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SparkWitchSounds.SAVIOR_PISTOL_CLICK, SoundCategory.PLAYERS, 0.5F, pitchVariation(shooter));

        // The server only accepts a real player within the revolver's lag tolerance; the aim itself is the client's.
        // 服务端只接受真实玩家且在左轮的延迟容差内；瞄准本身由客户端决定。
        ServerPlayerEntity target = null;
        if (world.getEntityById(targetEntityId) instanceof ServerPlayerEntity candidate
                && !candidate.isSpectator()
                && shooter.distanceTo(candidate) < SaviorRules.PISTOL_SERVER_TOLERANCE) {
            target = candidate;
        }
        // Record the shot exactly like Wathe's revolver instead of the custom pistol id. / 按 Wathe 左轮的身份记录，不用自定义手枪 id。
        GameRecordManager.recordItemUse(shooter, Registries.ITEM.getId(WatheItems.REVOLVER), target, null);

        GameWorldComponent game = GameWorldComponent.KEY.get(world);
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
     * Wathe's innocent-shooting punishment: take the pistol, and when the shooter cannot use killer features, drop a
     * revolver in its place (10-tick pickup delay), clear mood, block picking guns back up, and under
     * {@code KILL_SHOOTER} also kill the shooter. Runs four ticks after the shot, like Wathe.
     * Wathe 的误杀惩罚：收走救世主手枪；当射手不能使用杀手功能时在原地掉落一把左轮（10 刻拾取延迟）、清空理智、
     * 禁止再拾枪，并在 {@code KILL_SHOOTER} 策略下击杀射手。与 Wathe 一样在开枪四刻后执行。
     */
    private static void punish(ServerPlayerEntity shooter, GameWorldComponent game, ShootInnocentPunishment policy) {
        if (!takeOnePistol(shooter.getInventory())) {
            return;
        }
        if (game.canUseKillerFeatures(shooter)) {
            return;
        }
        ItemEntity dropped = shooter.dropItem(new ItemStack(WatheItems.REVOLVER), false, false);
        if (dropped != null) {
            dropped.setPickupDelay(SaviorRules.DROPPED_GUN_PICKUP_DELAY);
            dropped.setOwner(shooter.getUuid());
        }
        ServerPlayNetworking.send(shooter, new SaviorGunDropS2CPayload());
        PlayerMoodComponent.KEY.get(shooter).setMood(0.0F);
        game.addToPreventGunPickup(shooter);
        if (SaviorRules.isGoodPlayer(shooter, game) && policy == ShootInnocentPunishment.KILL_SHOOTER) {
            GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    private static boolean takeOnePistol(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.saviorPistol())) {
                inventory.removeStack(slot, 1);
                return true;
            }
        }
        return false;
    }

    private static float pitchVariation(ServerPlayerEntity player) {
        return 1.0F + player.getRandom().nextFloat() * 0.1F - 0.05F;
    }
}
