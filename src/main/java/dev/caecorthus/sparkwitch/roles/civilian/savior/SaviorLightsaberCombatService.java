package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberIgniteC2SPayload;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberSwingC2SPayload;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ShouldPiercePsychoArmour;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent.ShootInnocentPunishment;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/**
 * Server-side lightsaber sounds. The impact sound fires only when the main-hand lightsaber kills a player, at the
 * victim's position. The ignition and swing intents arrive from the client and are broadcast at the holder's position
 * so nearby players hear them with distance attenuation. All are positional server sounds; {@code sounds.json} keeps
 * no subtitle.
 * 光剑服务端音效。命中音只在主手光剑击杀玩家时、于被击杀者位置播放；点火与挥动意图由客户端发来，服务端在持有者位置
 * 广播，附近玩家可按距离衰减听到。全部为服务端位置音效；sounds.json 不设字幕。
 */
public final class SaviorLightsaberCombatService {
    /** Spam guard: an ignition may not be requested more than once per half second on the server. / 防刷：服务端每半秒最多播放一次点火音。 */
    private static final long IGNITE_SOUND_COOLDOWN_TICKS = 10L;
    /** Spam guard for swings; doAttack already runs at the attack rate. / 挥动防刷；doAttack 本身已按攻击速率运行。 */
    private static final long SWING_SOUND_COOLDOWN_TICKS = 4L;
    /** Wathe and vanilla death may both report one kill; one sound per victim within this window. / Wathe 与原版可能各报一次，同一死者窗口内只响一次。 */
    private static final long KILL_SOUND_DEDUPE_TICKS = 5L;
    private static final Map<UUID, Long> LAST_IGNITE_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_SWING_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_KILL_SOUND_TICK = new HashMap<>();
    private static boolean registered;

    private SaviorLightsaberCombatService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(SaviorLightsaberIgniteC2SPayload.ID,
                SaviorLightsaberIgniteC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaviorLightsaberIgniteC2SPayload.ID,
                (payload, context) -> playIgnition(context.player()));
        PayloadTypeRegistry.playC2S().register(SaviorLightsaberSwingC2SPayload.ID,
                SaviorLightsaberSwingC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaviorLightsaberSwingC2SPayload.ID,
                (payload, context) -> playSwing(context.player()));
        // Wathe kills (bat/gun/knife) and vanilla health deaths are separate paths; the victim-side dedupe merges them.
        // Wathe 击杀（球棒/枪/刀）与原版血量死亡是两条路径；按死者去重合并。
        KillPlayer.AFTER.register((victim, killer, reason) -> playKillSound(killer, victim));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity victim
                    && source.getAttacker() instanceof ServerPlayerEntity killer) {
                playKillSound(killer, victim);
            }
        });
        // Terminal reasons: SparkTraits treats these as unprotected kills (no Last Escape / Last Stand / Depression).
        // 终结原因：SparkTraits 视为不可保护的击杀（无绝处逢生/背水一战/抑郁保护）。
        SparkTraitsKillerBridge.registerTerminalDeathReason(SaviorRules.PISTOL_DEATH_REASON);
        SparkTraitsKillerBridge.registerTerminalDeathReason(SaviorRules.LIGHTSABER_DEATH_REASON);
        // Pierce psycho armour (the Jester moment's shields) for these terminal weapon kills. Wathe's real-Jester
        // fake death only reacts to GUN/DEMON_HUNTER, so the custom reasons already skip it; this covers the shields.
        // 穿透精神护甲（小丑时刻的护盾）。Wathe 真正小丑的假死只认 GUN/恶魔猎手，所以自定义死因已绕过假死；这里补护盾。
        ShouldPiercePsychoArmour.EVENT.register((victim, reason) ->
                SaviorRules.PISTOL_DEATH_REASON.equals(reason) || SaviorRules.LIGHTSABER_DEATH_REASON.equals(reason));
        AttackEntityCallback.EVENT.register(SaviorLightsaberCombatService::onSwordAttack);
    }

    /**
     * Broadcasts the ignition sound at the player's position. Server-side playback is positional and attenuated, so
     * nearby players hear it and it is not a client-local no-op.
     * 在玩家位置广播点火音。服务端播放是位置音效并随距离衰减，附近玩家都能听到，不是仅客户端本地播放。
     */
    private static void playIgnition(ServerPlayerEntity player) {
        if (player == null || player.isSpectator() || !holdsLightsaber(player)) {
            return;
        }
        long now = player.getWorld().getTime();
        Long last = LAST_IGNITE_TICK.get(player.getUuid());
        if (last != null && now - last < IGNITE_SOUND_COOLDOWN_TICKS) {
            return;
        }
        LAST_IGNITE_TICK.put(player.getUuid(), now);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SparkWitchSounds.SAVIOR_LIGHTSABER_IGNITE, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Lightsaber kill: only a fully charged main-hand swing while the blade is off item cooldown kills. The kill uses a
     * terminal death reason, so shields and Last Escape cannot save the victim; the sword then enters its 50 s cooldown.
     * 光剑击杀：只有主手、攻击冷却满、且武器不在物品冷却时命中才会击杀。击杀使用终结死因，护盾与绝处逢生都救不了，
     * 随后光剑进入 50 秒冷却。
     */
    private static ActionResult onSwordAttack(PlayerEntity player, World world, Hand hand, Entity target,
                                              EntityHitResult hitResult) {
        if (world.isClient
                || !(player instanceof ServerPlayerEntity attacker)
                || !(target instanceof ServerPlayerEntity victim)
                || attacker.isSpectator()
                || victim.isSpectator()) {
            return ActionResult.PASS;
        }
        ItemStack stack = attacker.getStackInHand(hand);
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())) {
            return ActionResult.PASS;
        }
        if (attacker.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return ActionResult.FAIL;
        }
        if (attacker.getAttackCooldownProgress(0.5F) < 1.0F) {
            return ActionResult.FAIL;
        }
        // Decide the innocent-kill punishment while the victim is still alive, like the pistol does.
        // 与手枪一致，在受害者仍存活时判定误杀惩罚。
        punishInnocentSwordKill(attacker, victim);
        GameRecordManager.recordItemUse(attacker, SaviorRules.LIGHTSABER_ID, victim, null);
        GameFunctions.killPlayer(victim, true, attacker, SaviorRules.LIGHTSABER_DEATH_REASON);
        SaviorCooldowns.set(attacker, stack.getItem(), SaviorRules.LIGHTSABER_KILL_COOLDOWN_TICKS);
        return ActionResult.SUCCESS;
    }

    /**
     * Sword innocent-kill punishment: mirrors the pistol's Shot Innocent policy. The lightsaber is taken and a revolver
     * is dropped in its place; the shooter loses the sword, mood is cleared (overridden by full sanity), gun pickup is
     * blocked and KILL_SHOOTER still kills the shooter. The pistol's random gun-backfire is not applied to a melee hit.
     * 光剑误杀惩罚：沿用与手枪相同的 Shot Innocent 策略。收走光剑并在原地掉落一把左轮；射手失去光剑、理智清零
     * （随后被恒满覆盖）、禁止拾枪；KILL_SHOOTER 仍会击杀射手。手枪的随机炸膛不适用于近战。
     */
    private static void punishInnocentSwordKill(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (attacker.isCreative() || !game.isInnocent(victim)) {
            return;
        }
        ShouldPunishGunShooter.PunishResult punishment =
                ShouldPunishGunShooter.EVENT.invoker().shouldPunish(attacker, victim);
        if (punishment != null && punishment.hasCustomPunishment()) {
            Scheduler.schedule(punishment::executeCustomPunishment, SaviorRules.PUNISH_DELAY_TICKS);
        } else if (punishment == null || punishment.shouldPunish()) {
            ShootInnocentPunishment policy = game.getShootInnocentPunishment();
            Scheduler.schedule(() -> punishSword(attacker, game, policy), SaviorRules.PUNISH_DELAY_TICKS);
        }
    }

    private static void punishSword(ServerPlayerEntity shooter, GameWorldComponent game,
                                    ShootInnocentPunishment policy) {
        if (!takeOneSword(shooter.getInventory())) {
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
        PlayerMoodComponent.KEY.get(shooter).setMood(0.0F);
        game.addToPreventGunPickup(shooter);
        if (game.isInnocent(shooter) && policy == ShootInnocentPunishment.KILL_SHOOTER) {
            GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    private static boolean takeOneSword(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.saviorLightsaber())) {
                inventory.removeStack(slot, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Broadcasts the swing sound at the player's position. Only the main hand counts, because vanilla left-click attacks
     * use the main hand; SparkTraits weapon locks suppress it just like a real attack.
     * 在玩家位置广播挥动音。只认主手，因为原版左键攻击使用主手；SparkTraits 封锁武器动作时同样不播放。
     */
    private static void playSwing(ServerPlayerEntity player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())
                || SparkTraitsKillerBridge.blocksWeaponAction(player, stack)) {
            return;
        }
        long now = player.getWorld().getTime();
        Long last = LAST_SWING_TICK.get(player.getUuid());
        if (last != null && now - last < SWING_SOUND_COOLDOWN_TICKS) {
            return;
        }
        LAST_SWING_TICK.put(player.getUuid(), now);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SparkWitchSounds.SAVIOR_LIGHTSABER_SWING, SoundCategory.PLAYERS, 1.0F,
                0.95F + world.random.nextFloat() * 0.1F);
    }

    private static boolean holdsLightsaber(PlayerEntity player) {
        return player.getMainHandStack().isOf(SparkWitchItems.saviorLightsaber())
                || player.getOffHandStack().isOf(SparkWitchItems.saviorLightsaber());
    }

    /**
     * Kill confirmation sound: only when the killer's main hand is the lightsaber, played at the victim's position.
     * 击杀确认音：仅当击杀者主手是光剑时，在被击杀者位置播放。
     */
    private static void playKillSound(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (killer == null || victim == null || killer == victim
                || !killer.getMainHandStack().isOf(SparkWitchItems.saviorLightsaber())) {
            return;
        }
        long now = victim.getWorld().getTime();
        Long last = LAST_KILL_SOUND_TICK.get(victim.getUuid());
        if (last != null && now - last < KILL_SOUND_DEDUPE_TICKS) {
            return;
        }
        LAST_KILL_SOUND_TICK.put(victim.getUuid(), now);
        ServerWorld world = victim.getServerWorld();
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SparkWitchSounds.SAVIOR_LIGHTSABER_HIT, SoundCategory.PLAYERS, 0.9F,
                0.9F + world.random.nextFloat() * 0.2F);
    }
}
