package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenTargeting;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionBackblastRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.Scheduler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server entries that let every weapon able to kill a player hit the Raven Form crow (plan v2 §2.5), one entry per
 * weapon seam, next to the Magician puppet ({@code MagicianPuppetHits}) and Seeker device ({@code SeekerDeviceHits})
 * entries at the same seams. A hit that would kill a player kills the Black Raven: the weapon's own player path runs on
 * the crow's owner through {@link BlackRavenCrowTargets#substitute} whenever the weapon has one, so the shooter pays
 * exactly what hitting the Raven directly costs (the Raven is an innocent police role: Wathe's revolver 小脑, the USEC,
 * Swordfish and Mighty Force misfire rules); the bat, sphere blasts (the M67, add-on blasts) and add-on guns use
 * {@link BlackRavenCrowSessions#killOwnerVia}. Nearest wins: a crow takes a ray or sweep only when strictly nearer
 * than the player the weapon would hit and than every Magician puppet and Seeker device it would end or break; blasts
 * measure the crow by the weapon's own player rule (Potion Gunner) or the puppet's sphere with a clear line (grenade,
 * M67, add-on blasts). A crow is never hit by its own owner, nor by a spectator, nor by an attacker SparkFactionAPI
 * refuses on its owner ({@link BlackRavenCrowTargets#mayAffect}).
 * Hitscan and client-picked checks read the crow's lag-compensated volumes ({@link BlackRavenCrowHitboxHistory}).
 * 让每件能杀死玩家的武器都能命中化鸦乌鸦的服务端入口（计划 v2 §2.5），每个武器接缝一个入口，与同一接缝上的魔术师皮套
 * （{@code MagicianPuppetHits}）和搜寻者设备（{@code SeekerDeviceHits}）入口并列。足以杀死玩家的命中杀死黑羽鸦：武器有自身
 * 玩家路径时，都经 {@link BlackRavenCrowTargets#substitute} 让该路径作用于乌鸦的主人，因此射手付出的代价与直接命中黑羽鸦
 * 完全相同（黑羽鸦是无辜的警察职业：Wathe 左轮小脑，USEC、剑鱼与巨力的误杀规则）；球棒、球形爆炸（M67、附属模组爆炸）与附属枪械使用
 * {@link BlackRavenCrowSessions#killOwnerVia}。最近者命中：只有严格近于武器本会命中的玩家、以及其本会结束或打坏的所有
 * 魔术师皮套与搜寻者设备时，乌鸦才承受射线或扫掠；爆炸按武器自身的玩家规则（药炮手）或皮套的视线畅通球体（手雷、M67、附属模组
 * 爆炸）量取乌鸦。乌鸦永远不会被其主人、旁观者或 SparkFactionAPI 禁止影响其主人的攻击者命中（{@link BlackRavenCrowTargets#mayAffect}）。
 * 即时射线与客户端选靶的检查读取乌鸦的延迟补偿体积（{@link BlackRavenCrowHitboxHistory}）。
 */
public final class BlackRavenCrowHits {
    /**
     * Delay of the add-on gun's custom punishment, as Wathe's revolver and the Serial Killer pistol schedule it.
     * 附属枪械自定义惩罚的延迟，与 Wathe 左轮及连环杀手手枪相同。
     */
    static final int ADDON_PUNISHMENT_DELAY_TICKS = 4;
    /**
     * Puppet box growth of the Hunter shotgun puppet hook ({@code MagicianPuppetHits.SHOTGUN_BOX_EXPANSION}), so the
     * crow and the puppet are measured as their own hooks measure them. / 猎枪皮套钩子的皮套箱体扩大量。
     */
    static final double SHOTGUN_PUPPET_GROWTH = 0.1;

    private BlackRavenCrowHits() {
    }

    // ---- Wathe receivers (client-picked ids) / Wathe 接收器（客户端选靶 id） ----

    /**
     * Wathe revolver/derringer receiver, at its {@code getEntityById} lookup: a crow id the shooter may hit, within
     * Wathe's 65 cap and on the shooter's look ray (lag-compensated), resolves to the crow's owner, so Wathe's whole
     * player path runs on the Black Raven (record, {@code ShouldPunishGunShooter}, the innocent-shot punishment and
     * backfire, the kill, the passenger mood loss). Anything else is returned unchanged (a refused crow id stays a miss),
     * including a crow whose owner SparkFactionAPI shields from the shooter for {@code wathe:gun}
     * ({@link BlackRavenCrowTargets#mayAffect}; its receiver guard only sees player ids): no punishment, no session end.
     * Wathe 左轮/德林加接收器，在其 {@code getEntityById} 查找处：射手可命中、位于 Wathe 65 格上限内且在射手视线上（延迟补偿）的
     * 乌鸦 id 解析为乌鸦的主人，因此 Wathe 的整条玩家路径都作用于黑羽鸦（记录、{@code ShouldPunishGunShooter}、误杀惩罚与走火、
     * 击杀、乘客理智损失）。其余情况原样返回（被拒的乌鸦 id 仍是未命中），包括 SparkFactionAPI 以 {@code wathe:gun} 禁止射手影响
     * 其主人的乌鸦（{@link BlackRavenCrowTargets#mayAffect}；其接收器守卫只识别玩家 id）：不惩罚、不结束会话。
     */
    public static @Nullable Entity onGunTargetLookup(ServerPlayerEntity shooter, @Nullable Entity resolved) {
        BlackRavenCrowTargets.forget(resolved);
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(shooter, resolved,
                GameConstants.DeathReasons.GUN);
        if (target == null || shooter.isSpectator()
                || !BlackRavenCrowTargets.gunAimed(shooter, target, BlackRavenCrowHitRules.GUN_MAX_DISTANCE)) {
            return resolved;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * Wathe knife receiver, at its {@code getEntityById} lookup: a crow id the attacker may hit within the stab reach
     * (3 + 0.5, lag-compensated) with a clear line resolves to its owner, so Wathe's stab (and every mod's stab hook)
     * runs on the Black Raven; a crow whose owner SparkFactionAPI shields from the attacker for {@code wathe:knife} stays
     * a non-player id, which Wathe's receiver ignores. / Wathe 刀接收器，在其 {@code getEntityById} 查找处：攻击者可命中、在刺击
     * 距离（3 + 0.5，延迟补偿）内且视线畅通的乌鸦 id 解析为其主人，因此 Wathe 的刺击（以及各模组的刺击钩子）作用于黑羽鸦；
     * SparkFactionAPI 以 {@code wathe:knife} 禁止攻击者影响其主人的乌鸦仍是非玩家 id，Wathe 接收器将其忽略。
     */
    public static @Nullable Entity onKnifeTargetLookup(ServerPlayerEntity attacker, @Nullable Entity resolved) {
        BlackRavenCrowTargets.forget(resolved);
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, resolved,
                GameConstants.DeathReasons.KNIFE);
        if (target == null || attacker.isSpectator()
                || !BlackRavenCrowTargets.inMeleeReach(attacker, target, BlackRavenCrowHitRules.KNIFE_REACH)) {
            return resolved;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * The receivers' own distance cap measures the body; a substituted owner was already measured on the crow, so it
     * passes (0). / 接收器自身的距离上限量的是本体；被替身的主人已按乌鸦量过距离，因此直接通过（0）。
     */
    public static float receiverDistance(Entity candidate, float original) {
        return BlackRavenCrowTargets.isSubstituted(candidate) ? 0.0F : original;
    }

    /**
     * The knife receiver's stab sound, played where the crow was hit instead of at the hidden body. True when played.
     * 刀接收器的刺击声在乌鸦被命中处播放，而不是在隐藏的本体处。已播放时返回 true。
     */
    public static boolean playAtSubstitutedCrow(Entity target, SoundEvent sound, float volume, float pitch) {
        Vec3d at = BlackRavenCrowTargets.substitutedCrowPos(target);
        if (at == null || !(target.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.PLAYERS, volume, pitch);
        return true;
    }

    // ---- Melee / 近战 ----

    /**
     * Wathe bat left-click ({@code BlackRavenCrowAttackHandlers}): like Wathe's own bat kill, the Wathe bat in the main
     * hand at full attack charge, plus the SparkTraits weapon gate, reach (3 + 1, lag-compensated) and line of sight,
     * then the SparkTraits parry; the owner dies as under a bat ({@code killOwnerVia}, {@code wathe:bat}) with Wathe's
     * bat sound at the crow, and the attack charge resets. True = consumed. Wathe's bat path has no innocent rule. A
     * crow whose owner SparkFactionAPI shields for {@code wathe:bat} (its attack guard only sees players) is not hit.
     * Wathe 球棒左键（{@code BlackRavenCrowAttackHandlers}）：与 Wathe 自身球棒击杀一致，主手 Wathe 球棒且攻击蓄满，另加
     * SparkTraits 武器门槛、距离（3 + 1，延迟补偿）与视线，然后是 SparkTraits 格挡；主人按球棒击杀死亡（{@code killOwnerVia}，
     * {@code wathe:bat}），在乌鸦处播放 Wathe 球棒声，并重置攻击蓄力。返回 true 表示已消费。Wathe 的球棒路径没有误杀规则。
     * SparkFactionAPI 以 {@code wathe:bat} 保护其主人的乌鸦（其攻击守卫只识别玩家）不会被命中。
     */
    public static boolean onBatHit(ServerPlayerEntity attacker, @Nullable Entity entity) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, entity,
                GameConstants.DeathReasons.BAT);
        if (target == null) {
            return false;
        }
        ItemStack bat = attacker.getMainHandStack();
        if (!bat.isOf(WatheItems.BAT) || attacker.getAttackCooldownProgress(0.5F) < 1.0F
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, bat)
                || !BlackRavenCrowTargets.inMeleeReach(attacker, target, BlackRavenCrowHitRules.MELEE_REACH)) {
            return false;
        }
        attacker.resetLastAttackedTicks();
        if (SparkTraitsKillerBridge.shouldCancelMeleeAttack(attacker, target.owner(), bat)) {
            return true;
        }
        Vec3d at = target.crow().getPos();
        BlackRavenCrowSessions.killOwnerVia(target.crow(), attacker, GameConstants.DeathReasons.BAT);
        attacker.getServerWorld().playSound(null, at.x, at.y, at.z, WatheSounds.ITEM_BAT_HIT, SoundCategory.PLAYERS,
                3.0F, 1.0F);
        return true;
    }

    /**
     * Left-click weapons whose own handler kills a player target (Mighty Force, the Ceremonial Sword strike): a crow the
     * attacker may hit within melee reach (3 + 1, lag-compensated) with a clear line becomes its owner, so the handler's
     * own gates, kill and misfire rule run on the Black Raven. Anything else is returned unchanged.
     * 自身处理器会击杀玩家目标的左键武器（巨力、仪礼剑左键）：攻击者可命中、在近战距离（3 + 1，延迟补偿）内且视线畅通的乌鸦
     * 变为其主人，因此处理器自身的门槛、击杀与误杀规则作用于黑羽鸦。其余情况原样返回。
     */
    public static @Nullable Entity meleeStandIn(ServerPlayerEntity attacker, @Nullable Entity entity) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, entity);
        if (target == null
                || !BlackRavenCrowTargets.inMeleeReach(attacker, target, BlackRavenCrowHitRules.MELEE_REACH)) {
            return entity;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * Client-picked stab weapons with their own reach (the Vendetta knife): like {@link #meleeStandIn} with
     * {@code reach} plus the 0.5 latency tolerance. / 自带距离的客户端选靶刺击武器（复仇者之刃）：与 {@link #meleeStandIn}
     * 相同，距离为 {@code reach} 加 0.5 延迟容差。
     */
    public static @Nullable Entity stabStandIn(ServerPlayerEntity attacker, @Nullable Entity entity, double reach) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, entity);
        if (target == null || !BlackRavenCrowTargets.inMeleeReach(attacker, target,
                reach + (BlackRavenCrowHitRules.KNIFE_REACH - 3.0))) {
            return entity;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * Add-on melee weapons with their own player path, through {@code SparkWitchApi.crowMeleeStandIn} (today
     * SparkStrength's Veteran, Coroner-disguise and Silencer knives and the Undercover tomahawk, whose handlers take
     * over before Wathe's receivers): a crow the attacker may hit within its {@code reach} plus
     * {@link BlackRavenCrowHitRules#ADDON_MELEE_TOLERANCE} (lag-compensated) with a clear line becomes its substituted
     * owner; null for anything else, so nothing is armed. The frozen signature carries no action, so the SparkFactionAPI
     * check uses the stab's {@code wathe:knife} (four of the five callers; each caller still checks its own action on the
     * returned owner, as SparkStrength's tomahawk does with {@code sparkstrength:tomahawk}).
     * 自带玩家路径的附属模组近战武器，经 {@code SparkWitchApi.crowMeleeStandIn} 调用（目前为 SparkStrength 老兵、验尸官伪装与
     * 沉默者的刀以及卧底战术斧，其处理器在 Wathe 接收器之前接管）：攻击者可命中、在其 {@code reach} 加
     * {@link BlackRavenCrowHitRules#ADDON_MELEE_TOLERANCE}（延迟补偿）内且视线畅通的乌鸦变为其被替身的主人；其余情况为 null，
     * 不装入任何替身。冻结签名不带行为 id，因此 SparkFactionAPI 判定使用刺击的 {@code wathe:knife}（五个调用方中的四个；各调用方
     * 仍会对返回的主人检查自身行为，如 SparkStrength 战术斧使用 {@code sparkstrength:tomahawk}）。
     */
    public static @Nullable ServerPlayerEntity addonMeleeStandIn(@Nullable ServerPlayerEntity attacker,
                                                                 @Nullable Entity entity, double reach) {
        if (attacker == null || entity == null || attacker.getWorld().isClient() || attacker.isSpectator()) {
            return null;
        }
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, entity,
                GameConstants.DeathReasons.KNIFE);
        if (target == null || !BlackRavenCrowTargets.inMeleeReach(attacker, target,
                BlackRavenCrowHitRules.addonMeleeReach(reach))) {
            return null;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * Ceremonial Sword dash step, after its player and puppet contact checks: the nearest crow the dasher may hit whose
     * current box meets the step's swept box is a dash contact like a player; its substituted owner is returned for the
     * sword kill, else null. / 仪礼剑冲刺的每一步，在玩家与皮套接触检查之后：当前箱体与该步扫掠箱相交、冲刺者可命中的最近乌鸦
     * 与玩家一样构成冲刺接触；返回其被替身的主人以执行剑杀，否则为 null。
     */
    public static @Nullable ServerPlayerEntity onDashContact(ServerPlayerEntity attacker, Box sweptBox) {
        if (attacker == null || sweptBox == null || !(attacker.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        BlackRavenCrowTargets.Target contact = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (BlackRavenCrowTargets.Target target : BlackRavenCrowTargets.live(world, attacker)) {
            double distance = target.crow().squaredDistanceTo(attacker);
            if (distance < nearest && target.crow().getBoundingBox().intersects(sweptBox)) {
                contact = target;
                nearest = distance;
            }
        }
        return contact == null ? null : BlackRavenCrowTargets.substitute(contact);
    }

    /**
     * Angler Swordfish stab submitted on a crow: the server's own aim ray ({@code start -> end}, block-clipped, the
     * Swordfish reach) must enter the crow's lag-compensated volumes grown by {@code aimTolerance} strictly nearer than
     * {@code beatSquared} (the nearest other eligible player, device or puppet on that ray), else null. The substituted
     * owner then takes the Swordfish's own path (costs, parry, kill, friendly-fire death).
     * 提交到乌鸦上的钓鱼佬剑鱼刺击：服务端自身的瞄准射线（{@code start -> end}，按方块截断，剑鱼距离）须严格早于
     * {@code beatSquared}（该射线上其他合格玩家、设备或皮套中最近者）进入乌鸦按 {@code aimTolerance} 扩大的延迟补偿体积，否则为
     * null。随后被替身的主人走剑鱼自身的路径（消耗、格挡、击杀、误伤致死）。
     */
    public static @Nullable ServerPlayerEntity onSwordfishStab(ServerPlayerEntity attacker, @Nullable Entity submitted,
                                                               Vec3d start, Vec3d end, double aimTolerance,
                                                               double beatSquared) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(attacker, submitted);
        if (target == null || !BlackRavenCrowHitRules.crowWins(
                BlackRavenCrowTargets.entrySquared(attacker, target, start, end, aimTolerance), beatSquared)) {
            return null;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    // ---- Server rays / 服务端射线 ----

    /**
     * Server look-ray picks that see players only (the SparkWitch Ninja Knife, a Magician puppet's replayed revolver or
     * knife): a crow strictly nearer on the attacker's block-clipped look ray of {@code range} than the player pick
     * {@code current} becomes the victim (its substituted owner, lag-compensated volumes), else null.
     * 只识别玩家的服务端视线选取（SparkWitch 忍者刀、魔术师皮套回放的左轮或刀）：在攻击者按方块截断、长 {@code range} 的视线上
     * 严格近于玩家选取 {@code current} 的乌鸦成为受害者（返回被替身的主人，延迟补偿体积），否则为 null。
     */
    public static @Nullable ServerPlayerEntity nearerOnLook(ServerPlayerEntity attacker, @Nullable Entity current,
                                                            double range) {
        if (!BlackRavenCrowTargets.anyLive(attacker)) {
            return null;
        }
        Vec3d start = attacker.getEyePos();
        Vec3d end = BlackRavenCrowTargets.clipToBlocks(attacker.getWorld(), start,
                start.add(attacker.getRotationVec(1.0F).multiply(range)), attacker);
        double beat = current == null ? Double.POSITIVE_INFINITY
                : boxDistanceSquared(start, end, current.getBoundingBox());
        BlackRavenCrowTargets.RayHit hit = BlackRavenCrowTargets.nearestOnRay(attacker, start, end, 0.0, beat);
        return hit == null ? null : BlackRavenCrowTargets.substitute(hit.target());
    }

    /**
     * Hunter double-barrel shotgun (server branch, after the shell is spent), before the puppet and device steps: the
     * crow takes the shot when the block-clipped ray ({@code eye -> end}) enters its lag-compensated volumes (grown by
     * {@code growth}, the claim confirm growth) strictly nearer than {@code aimed}'s volumes (same growth), every
     * puppet and every breakable device. Read-only, asked once per pellet ray. Returns the substituted owner with the
     * squared ray entry (the caller kills it as that ray's victim and skips the ray's puppet and device steps), else
     * null.
     * Hunter 双管猎枪（服务端分支、耗弹之后），在皮套与设备步骤之前：按方块截断的射线（{@code eye -> end}）严格早于
     * {@code aimed} 的体积（相同扩大量）、所有皮套与所有可打坏设备进入乌鸦延迟补偿体积（按 {@code growth} 扩大，即声明确认扩大量）
     * 时，乌鸦承受这一枪。只读，每条弹丸射线询问一次。返回被替身的主人及射线入射平方距离（调用方将其作为该射线的受害者击杀，
     * 并跳过该射线的皮套与设备步骤），否则为 null。
     */
    public static @Nullable ShotgunHit onShotgunFired(PlayerEntity user, @Nullable PlayerEntity aimed,
                                                      Vec3d eye, Vec3d end, double growth) {
        if (!(user instanceof ServerPlayerEntity shooter) || shooter.getWorld().isClient()
                || !GameWorldComponent.KEY.get(shooter.getWorld()).isRunning()
                || !BlackRavenCrowTargets.anyLive(shooter)) {
            return null;
        }
        double player = aimed == null ? Double.POSITIVE_INFINITY
                : BlackRavenCrowHitRules.entrySquared(eye, end, PlayerHitboxHistory.hitVolumes(shooter, aimed, growth));
        double beat = BlackRavenCrowHitRules.nearest(player < 0.0 ? Double.POSITIVE_INFINITY : player,
                BlackRavenCrowTargets.puppetSquared(shooter, eye, end, SHOTGUN_PUPPET_GROWTH),
                BlackRavenCrowTargets.deviceSquared(shooter, eye, end));
        BlackRavenCrowTargets.RayHit hit = BlackRavenCrowTargets.nearestOnRay(shooter, eye, end, growth, beat);
        return hit == null ? null : new ShotgunHit(BlackRavenCrowTargets.substitute(hit.target()),
                hit.distanceSquared());
    }

    /**
     * A shotgun ray's crow hit: the substituted owner plus the squared ray entry, so the buckshot orders its victims
     * like plain player hits. / 猎枪射线的乌鸦命中：被替身的主人及射线入射平方距离，使霰弹像普通玩家命中一样排列受害者。
     */
    public record ShotgunHit(ServerPlayerEntity owner, double entryDistanceSquared) {
    }

    /** A crow on a bullet path, with the path distance where the bullet enters it. / 子弹路径上的乌鸦及进入处的路径距离。 */
    public record PathHit(BlackRavenCrowTargets.Target target, double distance) {
    }

    /**
     * USEC rifle match shot, before the puppet and device steps: walking the bullet's polyline in flight order, the
     * first crow whose lag-compensated volumes (grown like the player pick, {@code growth}) the path enters strictly
     * before {@code reach} (the player the bullet would hit, else the path length). The caller then gives the puppet
     * and device steps the crow's distance as their reach, and when neither absorbs the shot it kills the substituted
     * owner through the USEC punishment path. Null when no crow is on the path before {@code reach}.
     * USEC 步枪对局射击，在皮套与设备步骤之前：按飞行顺序沿子弹折线，路径严格早于 {@code reach}（子弹本会命中的玩家，否则为路径
     * 长度）进入其延迟补偿体积（与玩家选取同样按 {@code growth} 扩大）的第一只乌鸦。调用方随后以乌鸦距离作为皮套与设备步骤的
     * reach；二者都未吸收时，经 USEC 惩罚路径击杀被替身的主人。{@code reach} 之前没有乌鸦时为 null。
     */
    public static @Nullable PathHit onUsecRifleFired(ServerPlayerEntity shooter, List<Vec3d> path, double reach,
                                                     double growth) {
        if (shooter == null || path == null || path.size() < 2 || !(reach > 0.0)
                || !(shooter.getWorld() instanceof ServerWorld world)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return null;
        }
        PathHit best = null;
        for (BlackRavenCrowTargets.Target target : BlackRavenCrowTargets.live(world, shooter)) {
            double distance = BlackRavenCrowHitRules.pathEntry(path,
                    BlackRavenCrowHitboxHistory.hitVolumes(shooter, target.crow(), growth), reach);
            if (distance >= 0.0 && distance < reach && (best == null || distance < best.distance())) {
                best = new PathHit(target, distance);
            }
        }
        return best;
    }

    /** A crow the Feather Blade aim reached, and the squared eye distance to where it entered. / 羽刃瞄准到达的乌鸦。 */
    public record FeatherHit(BlackRavenCrowTargets.Target target, double distanceSquared) {
    }

    /**
     * Black Raven Feather Blade, before the Seeker seam: on the blade's block-clipped aim ray (eye, the
     * {@link BlackRavenRules#FEATHER_REACH} along the look), the crow whose lag-compensated volumes (no margin, like the
     * player aim) the ray enters strictly nearer than the aimed player's rewound volumes and than every breakable device
     * takes the aim. The caller marks the crow's OWNER: the mark is player state that settles 20 s later through
     * {@code BlackRavenMarkRuntime} (the kill, and the marker's 小脑 for an innocent victim) and outlives the at most
     * 30 s crow. Null when the aim does not reach a crow first.
     * 黑羽鸦羽刃，在搜寻者接缝之前：在羽刃按方块截断的瞄准射线上（眼睛起、沿视线 {@link BlackRavenRules#FEATHER_REACH}），射线严格
     * 早于被瞄准玩家的回溯体积与所有可打坏设备进入其延迟补偿体积（无余量，与玩家瞄准一致）的乌鸦承受这次瞄准。调用方标记乌鸦的
     * 主人：标记是玩家状态，20 秒后经 {@code BlackRavenMarkRuntime} 结算（击杀，以及误杀好人时标记者小脑），并比最长 30 秒的乌鸦
     * 存续更久。瞄准未先到达乌鸦时为 null。
     */
    public static @Nullable FeatherHit onFeatherBladeFired(ServerPlayerEntity user,
                                                           BlackRavenTargeting.@Nullable Aim<? extends PlayerEntity> aim) {
        if (user == null || user.getWorld().isClient() || !BlackRavenCrowTargets.anyLive(user)) {
            return null;
        }
        Vec3d eye = user.getEyePos();
        Vec3d end = BlackRavenCrowTargets.clipToBlocks(user.getWorld(), eye,
                eye.add(user.getRotationVec(1.0F).multiply(BlackRavenRules.FEATHER_REACH)), user);
        double growth = BlackRavenTargeting.FEATHER_BOX_EXPANSION;
        double player = aim == null ? Double.POSITIVE_INFINITY : BlackRavenCrowHitRules.entrySquared(eye, end,
                PlayerHitboxHistory.hitVolumes(user, aim.player(), growth));
        double beat = BlackRavenCrowHitRules.nearest(player < 0.0 ? Double.POSITIVE_INFINITY : player,
                BlackRavenCrowTargets.deviceSquared(user, eye, end));
        BlackRavenCrowTargets.RayHit hit = BlackRavenCrowTargets.nearestOnRay(user, eye, end, growth, beat);
        return hit == null ? null : new FeatherHit(hit.target(), hit.distanceSquared());
    }

    /**
     * Murderous Witch Death Ray: a piercing ray, so every crow whose lag-compensated volumes (grown by the ray's player
     * {@code growth}) meet the visible ray adds its substituted owner to {@code targets} (unless the body is already
     * there); crows never cut the ray, like players. Call after the Seeker cut, with its visible distance.
     * 杀意魔女死光：穿透射线，因此延迟补偿体积（按射线对玩家的 {@code growth} 扩大）与可见射线相交的每只乌鸦都把被替身的主人
     * 加入 {@code targets}（本体已在其中时除外）；乌鸦与玩家一样从不截断射线。在搜寻者截断之后、以其可见距离调用。
     */
    public static List<ServerPlayerEntity> withPiercedCrows(ServerPlayerEntity caster, Vec3d start, Vec3d direction,
                                                            double visibleDistance, double growth,
                                                            List<ServerPlayerEntity> targets,
                                                            RayVolumeTest test) {
        if (caster == null || start == null || direction == null || !(visibleDistance > 0.0)
                || !(caster.getWorld() instanceof ServerWorld world)) {
            return targets;
        }
        List<ServerPlayerEntity> all = new ArrayList<>(targets);
        for (BlackRavenCrowTargets.Target target : BlackRavenCrowTargets.live(world, caster)) {
            if (!all.contains(target.owner()) && test.intersects(start, direction,
                    BlackRavenCrowHitboxHistory.hitVolumes(caster, target.crow(), growth), visibleDistance)) {
                all.add(BlackRavenCrowTargets.substitute(target));
            }
        }
        return all;
    }

    /** The piercing ray's own volume test ({@code MurderousWitchDeathRayRules.intersectsRay}). / 穿透射线自身的体积判定。 */
    @FunctionalInterface
    public interface RayVolumeTest {
        boolean intersects(Vec3d start, Vec3d direction, List<Box> volumes, double maxDistance);
    }

    /**
     * Potion Gunner launcher backblast lane: the nearest crow behind the gunner, measured with the player rule
     * ({@link PotionBackblastRules#hitDistance} on its box, clear COLLIDER line to {@link PotionBackblastRules#sightPoint},
     * the gunner's SparkFactionAPI permission on the owner, a living, non-Last-Escape owner), when strictly nearer than
     * {@code reach} (the player victim's distance, else the lane length; a tie goes to the player). Null otherwise.
     * 药炮手炮筒尾焰通道：药炮手身后最近的乌鸦，按玩家规则量取（以其箱体计算 {@link PotionBackblastRules#hitDistance}，到
     * {@link PotionBackblastRules#sightPoint} 的 COLLIDER 视线畅通，药炮手对主人的 SparkFactionAPI 许可，主人存活且不在最后逃脱），
     * 且严格近于 {@code reach}（玩家受害者的距离，否则为通道长度；距离相同时归玩家）。否则为 null。
     */
    public static PotionBackblastRules.@Nullable Hit<BlackRavenCrowTargets.Target> backblastLane(
            ServerPlayerEntity gunner, Vec3d origin, Vec3d backwards, double length, double reach, Identifier action) {
        if (gunner == null || !(gunner.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        List<PotionBackblastRules.Hit<BlackRavenCrowTargets.Target>> hits = new ArrayList<>();
        for (BlackRavenCrowTargets.Target target : BlackRavenCrowTargets.live(world, gunner)) {
            ServerPlayerEntity owner = target.owner();
            if (!GameFunctions.isPlayerPlayingAndAlive(owner) || SparkTraitsKillerBridge.isLastEscapeActive(owner)
                    || !SparkFactionApi.canAffectPlayer(gunner, owner, action, game)) {
                continue;
            }
            Box box = target.crow().getBoundingBox();
            double distance = PotionBackblastRules.hitDistance(origin, backwards, length, box);
            if (distance < 0.0) {
                continue;
            }
            Vec3d sight = PotionBackblastRules.sightPoint(origin, backwards, distance, box);
            hits.add(new PotionBackblastRules.Hit<>(target, distance,
                    SeekerDamageRules.segmentClear(world, origin, sight, gunner)));
        }
        return PotionBackblastRules.nearestHit(hits).filter(hit -> hit.distance() < reach).orElse(null);
    }

    // ---- Projectiles / 投射物 ----

    /**
     * Projectile entity hit decided by vanilla collision on the crow's current box (Ninja shuriken, Witch Maiden
     * crimson skull): a crow the thrower may hit becomes its substituted owner, which the projectile's own player branch
     * then hits; anything else is returned unchanged, including a crow whose owner SparkFactionAPI shields from the
     * thrower for its projectile action ({@link #projectileMayHit} normally kept it out of the collision already).
     * 由原版碰撞在乌鸦当前箱体上判定的投射物命中（忍者手里剑、巫女赤色凋灵之首）：投掷者可命中的乌鸦变为其被替身的主人，随后由
     * 投射物自身的玩家分支命中；其余情况原样返回，包括 SparkFactionAPI 以投射物行为禁止投掷者影响其主人的乌鸦（通常
     * {@link #projectileMayHit} 已将其排除在碰撞之外）。
     */
    public static @Nullable Entity projectileStandIn(@Nullable Entity thrower, @Nullable Entity hit) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(
                thrower instanceof ServerPlayerEntity player ? player : null, hit,
                BlackRavenCrowHitRules.PROJECTILE_ACTION);
        if (target == null || thrower != null && !(thrower instanceof ServerPlayerEntity)) {
            return hit;
        }
        return BlackRavenCrowTargets.substitute(target);
    }

    /**
     * {@code canHit} filter of SparkWitch's own projectiles (Ninja shuriken, Witch Maiden skull, Potion Gunner shell):
     * false only for a server crow whose owner SparkFactionAPI shields from the player {@code thrower} for its
     * projectile action, so the crow is transparent to that projectile exactly as SparkFactionAPI's
     * {@code getOtherEntities} filter makes a shielded player transparent. Anything else is true.
     * SparkWitch 自有投射物（忍者手里剑、巫女凋灵之首、药炮手炮弹）的 {@code canHit} 过滤：只有当 SparkFactionAPI 以投射物行为
     * 禁止玩家 {@code thrower} 影响其主人时，服务端乌鸦返回 false，使乌鸦对该投射物透明，正如 SparkFactionAPI 的
     * {@code getOtherEntities} 过滤让受保护的玩家透明。其余情况为 true。
     */
    public static boolean projectileMayHit(@Nullable Entity thrower, @Nullable Entity entity) {
        if (!(entity instanceof BlackRavenCrowEntity crow) || crow.getWorld().isClient()) {
            return true;
        }
        return BlackRavenCrowTargets.mayAffect(thrower, BlackRavenCrowSessions.ownerOf(crow),
                BlackRavenCrowHitRules.PROJECTILE_ACTION);
    }

    /**
     * Read-only tick-head check for a projectile whose Seeker device sweep runs before vanilla collision (Ninja shuriken,
     * Witch Maiden crimson skull, Potion Gunner shell): true when a crow the thrower may hit lies on this tick's
     * block-clipped segment (current box grown by the vanilla 0.3 projectile margin) strictly before every Seeker device
     * the thrower may break. The caller then skips the device sweep this tick, so vanilla collision takes the crow (or a
     * still nearer entity). Ends nothing itself. A crow transparent to the thrower ({@link #projectileMayHit}) never
     * counts.
     * 供“搜寻者设备扫掠先于原版碰撞”的投射物（忍者手里剑、巫女赤色凋灵之首、药炮手炮弹）在 tick 开头做的只读检查：投掷者可命中的
     * 乌鸦位于本刻按方块截断的线段上（当前箱体按原版 0.3 投射物余量扩大），且严格先于投掷者可打坏的所有搜寻者设备时返回 true。
     * 调用方随即跳过本刻的设备扫掠，交由原版碰撞处理（命中该乌鸦或更近的实体）。本方法自身不处死任何人。对投掷者透明的乌鸦
     * （{@link #projectileMayHit}）从不计入。
     */
    public static boolean crowBeforeDevice(Entity projectile, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        if (projectile == null || from == null || to == null || !(projectile.getWorld() instanceof ServerWorld world)
                || !(thrower instanceof ServerPlayerEntity attacker)) {
            return false;
        }
        List<BlackRavenCrowTargets.Target> crows = BlackRavenCrowTargets.live(world, attacker,
                BlackRavenCrowHitRules.PROJECTILE_ACTION);
        if (crows.isEmpty()) {
            return false;
        }
        Vec3d end = BlackRavenCrowTargets.clipToBlocks(world, from, to, projectile);
        double device = BlackRavenCrowTargets.deviceSquared(attacker, from, end);
        if (device == Double.POSITIVE_INFINITY) {
            return false;
        }
        for (BlackRavenCrowTargets.Target target : crows) {
            double entry = BlackRavenCrowHitRules.entrySquared(from, end,
                    List.of(target.crow().getBoundingBox().expand(BlackRavenCrowHitRules.PROJECTILE_MARGIN)));
            if (BlackRavenCrowHitRules.crowWins(entry, device)) {
                return true;
            }
        }
        return false;
    }

    /**
     * NoellesRoles throwing axe pierce sweep (current boxes, no margin and no block clip, exactly its player loop):
     * every crow the thrower may hit whose box this tick's segment enters strictly before {@code cutSquared} (the
     * nearest breakable Seeker device on the segment), skipping crows whose owner SparkFactionAPI shields from the
     * thrower for the projectile action (the axe's player sweep is filtered the same way). Returns the substituted
     * owners; the caller hands each to the axe's own {@code onEntityHit}. / NoellesRoles 飞斧贯穿扫掠（当前箱体、无余量、
     * 不按方块截断，与其玩家循环完全一致）：本刻线段严格早于 {@code cutSquared}（线段上最近的可打坏搜寻者设备）进入其箱体、且
     * 投掷者可命中的每只乌鸦；跳过 SparkFactionAPI 以投射物行为禁止投掷者影响其主人的乌鸦（飞斧的玩家扫掠同样如此过滤）。返回
     * 被替身的主人；调用方把每个主人交给飞斧自身的 {@code onEntityHit}。
     */
    public static List<ServerPlayerEntity> onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from,
                                                              Vec3d to) {
        List<ServerPlayerEntity> owners = new ArrayList<>();
        if (axe == null || from == null || to == null || !(axe.getWorld() instanceof ServerWorld world)) {
            return owners;
        }
        ServerPlayerEntity attacker = thrower instanceof ServerPlayerEntity player ? player : null;
        if (thrower != null && attacker == null) {
            return owners;
        }
        // Live crows first (runs per axe per tick): with none, the Seeker-device box query is skipped. Both reads are
        // side-effect free, so swapping them leaves the result unchanged.
        // 先取存活乌鸦（每把飞斧每 tick 执行）：没有乌鸦时跳过搜寻者设备的箱体查询。两次读取都无副作用，交换顺序不改变结果。
        List<BlackRavenCrowTargets.Target> crows = BlackRavenCrowTargets.live(world, attacker,
                BlackRavenCrowHitRules.PROJECTILE_ACTION);
        if (crows.isEmpty()) {
            return owners;
        }
        double cut = attacker == null ? Double.POSITIVE_INFINITY
                : BlackRavenCrowTargets.deviceSquared(attacker, from, to);
        for (BlackRavenCrowTargets.Target target : crows) {
            double entry = BlackRavenCrowHitRules.entrySquared(from, to, List.of(target.crow().getBoundingBox()));
            if (BlackRavenCrowHitRules.crowWins(entry, cut)) {
                owners.add(BlackRavenCrowTargets.substitute(target));
            }
        }
        return owners;
    }

    // ---- Blasts / 爆炸 ----

    /**
     * Wathe grenade, after its collision exploded ({@code BlackRavenCrowGrenadeMixin}): the crow pass of the blast,
     * separate from Wathe's player loop (which SparkStrength refilters by each player's own box, so a crow's owner whose
     * body is elsewhere could never die there). {@link #onBlast} with the grenade's 3-block sphere around its box centre
     * and a clear line from it, {@code wathe:grenade}, the online thrower credited and the whole pass inside the
     * thrower's Judge attribution ({@code responsible}, the projectile's owner UUID, even offline). Owners in
     * {@code attempted} (the loop already tried to kill their body) are skipped, so one grenade never hits a Black Raven
     * twice; dead owners have no live crow. Returns how many crows were hit.
     * Wathe 手雷在碰撞爆炸之后（{@code BlackRavenCrowGrenadeMixin}）：独立于 Wathe 玩家循环的乌鸦扫描（SparkStrength 会按每名玩家
     * 自身箱体重新筛选该循环，本体在别处的乌鸦主人在那里永远死不了）。以手雷箱体中心 3 格球体且视线畅通调用 {@link #onBlast}，
     * {@code wathe:grenade}，记在在线投掷者名下，整个扫描在投掷者的审判官归因内执行（{@code responsible}，投射物主人 UUID，离线亦可）。
     * 跳过 {@code attempted} 中的主人（循环已尝试击杀其本体），因此一颗手雷永远不会两次命中同一黑羽鸦；已死亡的主人没有存活乌鸦。
     * 返回被命中的乌鸦数量。
     */
    public static int onGrenadeBlast(ServerWorld world, Vec3d center, @Nullable ServerPlayerEntity thrower,
                                     @Nullable UUID responsible, Set<UUID> attempted) {
        if (world == null || center == null || BlackRavenCrowTargets.live(world, null).isEmpty()) {
            return 0;
        }
        int[] hit = {0};
        JudgeKillAttribution.runWith(world, responsible, () -> hit[0] = onBlast(world, center,
                BlackRavenCrowHitRules.GRENADE_RADIUS, thrower, GameConstants.DeathReasons.GRENADE,
                attempted == null ? Set.of() : attempted));
        return hit[0];
    }

    /**
     * SparkStrength M67 blast, from {@code SparkStrengthM67Compat} after its match-grenade gate (SparkStrength kills the
     * players itself): every crow whose box centre lies in the M67 kill sphere with a clear line from the centre (the
     * Magician puppet's measure) kills its owner ({@code killOwnerVia}, {@code wathe:grenade}, the thrower credited).
     * Never the thrower's own crow. / SparkStrength M67 爆炸，由 {@code SparkStrengthM67Compat} 在其对局手雷门槛之后调用
     * （玩家由 SparkStrength 自行击杀）：箱体中心位于 M67 击杀球内且与爆心视线畅通（魔术师皮套的量法）的每只乌鸦处死其主人
     * （{@code killOwnerVia}，{@code wathe:grenade}，记在投掷者名下）。从不包括投掷者自己的乌鸦。
     */
    public static void onM67Blast(ServerWorld world, Vec3d center, @Nullable ServerPlayerEntity thrower) {
        onBlast(world, center, BlackRavenCrowHitRules.M67_RADIUS, thrower, GameConstants.DeathReasons.GRENADE);
    }

    /**
     * Sphere blast whose weapon kills the players in it on its own (the SparkStrength M67, and through
     * {@code SparkWitchApi.killCrowsInBlast} add-on blasts such as SparkStrength's bomb drone): every crow whose box
     * centre lies in the closed {@code radius} sphere with a clear line from {@code center} (the Magician puppet's
     * measure) kills its owner ({@code killOwnerVia}, {@code killer} credited, {@code deathReason} passed through as the
     * same instance). Never the killer's own crow, nor one whose owner SparkFactionAPI shields from {@code killer} for
     * {@code deathReason} (left alone, not counted); only while a Wathe round runs. Returns how many crows were hit.
     * 武器自行击杀范围内玩家的球形爆炸（SparkStrength M67，以及经 {@code SparkWitchApi.killCrowsInBlast} 转入的附属模组爆炸，
     * 如 SparkStrength 炸弹无人机）：箱体中心位于闭球 {@code radius} 内且与 {@code center} 视线畅通（魔术师皮套的量法）的每只乌鸦
     * 处死其主人（{@code killOwnerVia}，记在 {@code killer} 名下，{@code deathReason} 按同一实例原样传递）。从不包括击杀者自己的
     * 乌鸦，也不包括 SparkFactionAPI 以 {@code deathReason} 禁止 {@code killer} 影响其主人的乌鸦（保持不动、不计数）；仅在 Wathe
     * 对局进行中生效。返回被命中的乌鸦数量。
     */
    public static int onBlast(@Nullable ServerWorld world, @Nullable Vec3d center, double radius,
                              @Nullable ServerPlayerEntity killer, @Nullable Identifier deathReason) {
        return onBlast(world, center, radius, killer, deathReason, Set.of());
    }

    /** {@link #onBlast} skipping the owners in {@code skipOwners}. / 跳过 {@code skipOwners} 中主人的 {@link #onBlast}。 */
    static int onBlast(@Nullable ServerWorld world, @Nullable Vec3d center, double radius,
                       @Nullable ServerPlayerEntity killer, @Nullable Identifier deathReason, Set<UUID> skipOwners) {
        if (world == null || center == null || deathReason == null || !(radius >= 0.0) || !Double.isFinite(radius)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return 0;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        List<BlackRavenCrowEntity> hit = new ArrayList<>();
        for (BlackRavenCrowTargets.Target target : BlackRavenCrowTargets.live(world, null)) {
            Box box = target.crow().getBoundingBox();
            ServerPlayerEntity owner = target.owner();
            if ((killer == null || !killer.getUuid().equals(owner.getUuid()))
                    && !skipOwners.contains(owner.getUuid()) && !game.isPlayerDead(owner.getUuid())
                    && BlackRavenCrowHitRules.inBlastSphere(center, box.getCenter(), radius)
                    && SeekerDamageRules.hasLineOfSight(world, center, box, null)
                    && BlackRavenCrowTargets.mayAffect(killer, owner, deathReason)) {
                hit.add(target.crow());
            }
        }
        for (BlackRavenCrowEntity crow : hit) {
            BlackRavenCrowSessions.killOwnerVia(crow, killer, deathReason);
        }
        return hit.size();
    }

    // ---- Add-on guns / 附属枪械 ----

    /**
     * Add-on gun shot through {@code SparkWitchApi.hitGunWorldTarget} (today SparkStrength's Serial Killer pistols),
     * after the facade's gun checks: a crow the shooter may hit within {@code maxDistance} (capped at Wathe's 65) on the
     * shooter's look ray (lag-compensated) kills its owner as that gun kills a player: the SparkFactionAPI gun veto, the
     * {@code ShouldPunishGunShooter} custom punishment (scheduled like the caller's), then {@code killOwnerVia} with
     * {@code wathe:gun}. The caller still finishes the shot as a recorded miss. True when the crow was hit.
     * 经 {@code SparkWitchApi.hitGunWorldTarget} 转入的附属模组枪械射击（目前为 SparkStrength 连环杀手手枪），在门面的枪械检查
     * 之后：射手可命中、位于 {@code maxDistance}（上限为 Wathe 的 65）内且在射手视线上（延迟补偿）的乌鸦，按该枪击杀玩家的方式
     * 处死其主人：SparkFactionAPI 枪械否决、{@code ShouldPunishGunShooter} 自定义惩罚（与调用方一样延迟执行），然后以
     * {@code wathe:gun} 调用 {@code killOwnerVia}。调用方仍按未命中记录收尾。命中乌鸦时返回 true。
     */
    public static boolean onAddonGunShot(ServerPlayerEntity shooter, @Nullable Entity entity, double maxDistance) {
        BlackRavenCrowTargets.Target target = BlackRavenCrowTargets.hittable(shooter, entity);
        if (target == null || shooter.isSpectator() || !(maxDistance > 0.0)
                || !BlackRavenCrowTargets.gunAimed(shooter, target,
                Math.min(maxDistance, BlackRavenCrowHitRules.GUN_MAX_DISTANCE))) {
            return false;
        }
        ServerPlayerEntity owner = target.owner();
        GameWorldComponent game = GameWorldComponent.KEY.get(shooter.getWorld());
        if (!SparkFactionApi.canAffectPlayer(shooter, owner, GameConstants.DeathReasons.GUN, game)) {
            return false;
        }
        ShouldPunishGunShooter.PunishResult punish = ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, owner);
        if (punish != null && punish.hasCustomPunishment()) {
            Scheduler.schedule(punish::executeCustomPunishment, ADDON_PUNISHMENT_DELAY_TICKS);
        }
        BlackRavenCrowSessions.killOwnerVia(target.crow(), shooter, GameConstants.DeathReasons.GUN);
        return true;
    }

    // ---- Internal / 内部 ----

    /** Ray entry into a box, or its plain distance when the ray starts inside or skims it. / 射线进入箱体的平方距离。 */
    private static double boxDistanceSquared(Vec3d start, Vec3d end, Box box) {
        double entry = BlackRavenCrowHitRules.entrySquared(start, end, List.of(box));
        return entry >= 0.0 ? entry : box.squaredMagnitude(start);
    }
}
