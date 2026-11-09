package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberIgniteC2SPayload;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberSwingC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowTargets;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackManager;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ShouldPiercePsychoArmour;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent.ShootInnocentPunishment;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Server half of the lightsaber: the kill (a full-charge main-hand strike from {@code AttackEntityCallback}, behind
 * the role binding and the SparkTraits / Vendetta / SparkFactionAPI melee gates), the innocent-kill punishment and the
 * 30 s post-kill cooldown, plus its sounds. The impact sound fires only when the main-hand lightsaber kills a player,
 * at the victim's position; the ignition and swing intents arrive from the client and are broadcast at the holder's
 * position with distance attenuation. {@code sounds.json} keeps no subtitle.
 * 光剑的服务端部分：击杀（{@code AttackEntityCallback} 中满蓄力的主手攻击，经过职业绑定与 SparkTraits / 复仇者 /
 * SparkFactionAPI 近战门控）、误杀惩罚与击杀后 30 秒冷却，以及其音效。命中音只在主手光剑击杀玩家时、于被击杀者位置播放；
 * 点火与挥动意图由客户端发来，服务端在持有者位置按距离衰减广播。sounds.json 不设字幕。
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
        // The spam/dedupe maps are per player UUID; drop them on leave and on server stop so they never grow forever.
        // 防刷/去重表按玩家 UUID 记录；离线与服务器停止时清除，避免无限增长。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
    }

    private static void forget(UUID player) {
        LAST_IGNITE_TICK.remove(player);
        LAST_SWING_TICK.remove(player);
        LAST_KILL_SOUND_TICK.remove(player);
    }

    private static void clearAll() {
        LAST_IGNITE_TICK.clear();
        LAST_SWING_TICK.clear();
        LAST_KILL_SOUND_TICK.clear();
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
     * Lightsaber kill: only a fully charged swing while the blade is off item cooldown kills, and while a game runs only
     * in a living Savior's hand ({@link SaviorRules#mayUseWeapons}). Gates in order, like the Swordfish and the
     * Ceremonial Sword: the SparkTraits weapon gate, cooldown and charge, an ordinary living survival target, the
     * SparkFactionAPI veto, then the SparkTraits melee parry (Close Quarters / forced melee lock), and only then the
     * innocent-kill punishment and the terminal kill. The 30 s cooldown follows only a real kill. A Black Raven crow in
     * reach stands in for its owner; a Magician puppet or a SparkStrength Partygoer takes the strike as a kill (decoy
     * rule D3: same 30 s cost). Every refused strike answers FAIL, so it never falls through to vanilla damage.
     * Creative mode ignores both the cooldown gate and the post-kill cooldown.
     * 光剑击杀：只有蓄满且武器不在物品冷却时的一击才会击杀；对局进行中还必须握在存活救世主手里
     * （{@link SaviorRules#mayUseWeapons}）。门槛顺序与剑鱼、仪礼剑一致：SparkTraits 武器门槛、冷却与蓄力、普通存活的生存
     * 模式目标、SparkFactionAPI 否决，然后是 SparkTraits 近战格挡（近身格斗 / 强制近战锁），最后才是误杀惩罚与终结击杀。
     * 30 秒冷却只在真正击杀后生效。近战距离内的黑羽鸦乌鸦以其主人代入；魔术师皮套或 SparkStrength 派对客按一次击杀承受这一击
     * （诱饵规则 D3：同样 30 秒代价）。所有被拒绝的攻击都返回 FAIL，绝不回退为原版伤害。创造模式同时忽略冷却门槛与击杀后冷却。
     */
    private static ActionResult onSwordAttack(PlayerEntity player, World world, Hand hand, Entity target,
                                              EntityHitResult hitResult) {
        if (world.isClient || !(player instanceof ServerPlayerEntity attacker) || attacker.isSpectator()) {
            return ActionResult.PASS;
        }
        ItemStack stack = attacker.getStackInHand(hand);
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())) {
            return ActionResult.PASS;
        }
        // Raven Form seam: a crow in reach stands in for its owner, so the strike and its misfire rule hit the Raven. A
        // refused strike disarms the stand-in again, so no later kill this tick ends that crow session.
        // 化鸦接缝：近战距离内的乌鸦以其主人代入，因此这一击及其误杀规则作用于黑羽鸦。被拒绝的攻击会解除替身，
        // 本刻之后的其他击杀不会因此结束该乌鸦会话。
        Entity resolved = BlackRavenCrowHits.meleeStandIn(attacker, target);
        try {
            return strike(attacker, world, stack, resolved);
        } finally {
            BlackRavenCrowTargets.forget(resolved);
        }
    }

    private static ActionResult strike(ServerPlayerEntity attacker, World world, ItemStack stack, Entity resolved) {
        boolean puppet = MagicianPuppetHits.isHittablePuppet(attacker, resolved);
        boolean partygoer = SparkStrengthPartygoerCompat.isPartygoer(resolved);
        if (!(resolved instanceof ServerPlayerEntity) && !puppet && !partygoer) {
            // Other entities keep their vanilla behaviour. / 其他实体保持原版行为。
            return ActionResult.PASS;
        }
        if (resolved instanceof ServerPlayerEntity candidate && candidate.isSpectator()) {
            return ActionResult.PASS;
        }
        if (!SaviorRules.mayUseWeapons(attacker) || SparkTraitsKillerBridge.blocksWeaponAction(attacker, stack)) {
            return ActionResult.FAIL;
        }
        // Creative mode ignores the weapon cooldown entirely (testing/building). / 创造模式完全忽略武器冷却（测试/建造）。
        boolean creative = attacker.isCreative();
        if (!creative && attacker.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return ActionResult.FAIL;
        }
        if (attacker.getAttackCooldownProgress(0.5F) < 1.0F) {
            return ActionResult.FAIL;
        }
        if (puppet) {
            return strikePuppet(attacker, (MagicianPlaybackEntity) resolved, stack, creative);
        }
        if (partygoer) {
            return strikePartygoer(attacker, resolved, stack, creative);
        }
        ServerPlayerEntity victim = (ServerPlayerEntity) resolved;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (victim == attacker
                || !VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(attacker, victim)
                || !GameFunctions.isPlayerAliveAndSurvival(victim)
                || !SaviorRules.mayAffect(attacker, victim, SaviorRules.LIGHTSABER_DEATH_REASON,
                SaviorRules.LIGHTSABER_DEATH_REASON, game)) {
            return ActionResult.FAIL;
        }
        // Close Quarters parry / forced melee lock, after eligibility and before any real side effect.
        // 近身格斗格挡 / 强制近战锁：在资格校验之后、任何实际副作用之前。
        if (SparkTraitsKillerBridge.shouldCancelMeleeAttack(attacker, victim, stack)) {
            return ActionResult.FAIL;
        }
        // Decide the innocent-kill punishment while the victim is still alive, like the pistol does.
        // 与手枪一致，在受害者仍存活时判定误杀惩罚。
        punishInnocentSwordKill(attacker, victim);
        GameRecordManager.recordItemUse(attacker, SaviorRules.LIGHTSABER_ID, victim, null);
        SaviorRules.settleKill(victim, attacker, SaviorRules.LIGHTSABER_DEATH_REASON);
        // "击杀 30s": only a strike that actually killed costs the cooldown. / 「击杀 30 秒」：只有真正击杀才进入冷却。
        if (!creative && !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            SaviorCooldowns.writeCooldown(attacker, stack.getItem(), SaviorRules.LIGHTSABER_KILL_COOLDOWN_TICKS);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Magician puppet: a live foreign puppet within melee reach (3 + 1) and line of sight ends as under a sword kill
     * (decoy body, the Magician is paid; D3/D4) and the lightsaber pays its kill cost: the 30 s cooldown and the hit
     * sound at the puppet. No innocent rule, no record line (the forced end records the replay).
     * 魔术师皮套：近战距离（3 + 1）内且视线畅通的他人活皮套按一次剑杀结束（诱饵尸体、魔术师获得报酬；D3/D4），光剑付出
     * 击杀代价：30 秒冷却与在皮套处播放的命中音。无误杀规则，不写使用记录（由强制结束记录回放）。
     */
    private static ActionResult strikePuppet(ServerPlayerEntity attacker, MagicianPlaybackEntity puppet, ItemStack stack,
                                             boolean creative) {
        if (!inMeleeReach(attacker, puppet)) {
            return ActionResult.FAIL;
        }
        Vec3d at = puppet.getPos();
        if (!MagicianPlaybackManager.endPuppet(puppet, attacker, SaviorRules.LIGHTSABER_DEATH_REASON,
                stack.getTranslationKey())) {
            return ActionResult.FAIL;
        }
        afterNonPlayerKill(attacker, stack, creative, at);
        return ActionResult.SUCCESS;
    }

    /**
     * SparkStrength Partygoer (owner rule 2026-10-21: whatever kills a player kills a Partygoer in one hit), with the
     * same reach and line-of-sight re-check; it is never an innocent, and SparkStrength records the kill.
     * SparkStrength 派对客（所有者规则 2026-10-21：能杀死玩家的东西一击打死派对客），同样复查距离与视线；它永远不是好人，
     * 由 SparkStrength 记录这次击杀。
     */
    private static ActionResult strikePartygoer(ServerPlayerEntity attacker, Entity partygoer, ItemStack stack,
                                                boolean creative) {
        if (!SparkStrengthPartygoerCompat.isKillable(partygoer) || partygoer.getWorld() != attacker.getWorld()
                || !inMeleeReach(attacker, partygoer)) {
            return ActionResult.FAIL;
        }
        Vec3d at = partygoer.getPos();
        if (!SparkStrengthPartygoerCompat.killByWeapon(attacker, partygoer, SaviorRules.LIGHTSABER_DEATH_REASON)) {
            return ActionResult.FAIL;
        }
        afterNonPlayerKill(attacker, stack, creative, at);
        return ActionResult.SUCCESS;
    }

    /** Vanilla checks reach only; re-check it and COLLIDER line of sight. / 原版只查距离，补查距离与 COLLIDER 视线。 */
    private static boolean inMeleeReach(ServerPlayerEntity attacker, Entity target) {
        Vec3d eye = attacker.getEyePos();
        double reach = SaviorRules.LIGHTSABER_MELEE_REACH;
        return SeekerDamageRules.squaredDistanceToBox(eye, target.getBoundingBox()) <= reach * reach
                && SeekerDamageRules.hasLineOfSight(attacker.getWorld(), eye, target.getBoundingBox(), attacker);
    }

    private static void afterNonPlayerKill(ServerPlayerEntity attacker, ItemStack stack, boolean creative, Vec3d at) {
        if (!creative) {
            SaviorCooldowns.writeCooldown(attacker, stack.getItem(), SaviorRules.LIGHTSABER_KILL_COOLDOWN_TICKS);
        }
        ServerWorld world = attacker.getServerWorld();
        world.playSound(null, at.x, at.y, at.z, SparkWitchSounds.SAVIOR_LIGHTSABER_HIT, SoundCategory.PLAYERS, 0.9F,
                0.9F + world.random.nextFloat() * 0.2F);
    }

    /**
     * Sword innocent-kill punishment: mirrors the pistol's Shot Innocent policy, four ticks later and only on a shooter
     * who is still the living Savior. The lightsaber is taken and the Savior's own hidden revolver drops (count kept,
     * like Wathe); mood is cleared (overridden by full sanity), gun pickup is blocked and KILL_SHOOTER still kills the
     * shooter. The pistol's random gun-backfire is not applied to a melee hit.
     * 光剑误杀惩罚：沿用与手枪相同的 Shot Innocent 策略，四刻后执行且只作用于仍存活的救世主。收走光剑并掉落救世主自己的
     * 隐藏左轮（与 Wathe 一样数量不变）；理智清零（随后被恒满覆盖）、禁止拾枪；KILL_SHOOTER 仍会击杀射手。手枪的随机炸膛
     * 不适用于近战。
     */
    private static void punishInnocentSwordKill(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (attacker.isCreative() || !SaviorRules.isGoodPlayer(victim, game)) {
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
        if (!SaviorRules.isPunishable(shooter, game)
                || !SaviorRules.takeOne(shooter.getInventory(), SparkWitchItems.saviorLightsaber())) {
            return;
        }
        if (game.canUseKillerFeatures(shooter)) {
            return;
        }
        SaviorRules.dropHiddenRevolver(shooter);
        PlayerMoodComponent.KEY.get(shooter).setMood(0.0F);
        game.addToPreventGunPickup(shooter);
        if (SaviorRules.isGoodPlayer(shooter, game) && policy == ShootInnocentPunishment.KILL_SHOOTER) {
            GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
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
