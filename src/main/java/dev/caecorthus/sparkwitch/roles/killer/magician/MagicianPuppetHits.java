package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionBackblastRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastGeometry;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastRings;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.demonhunter.DemonHunterPistolItem;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server entries that end a Magician puppet when a weapon hits it, one entry per damage source, mirroring the Seeker
 * device hits ({@code SeekerDeviceHits}). Every accepted hit ends in {@link MagicianPlaybackManager#endPuppet}, which
 * alone leaves the decoy, pays the Magician and records the replay. Owner decisions 2026-10-07: a hit costs the
 * attacker what a real hit costs (D3: gun sound, muzzle flash, spent derringer and cooldown; knife cooldown) but never
 * the innocent-shot punishment or mood loss; the Magician can never end its own puppet; spectators never hit one.
 * Client-targeted entries (gun, knife, Demon Hunter pistol, Swordfish, left-click melee) validate weapon, reach, aim
 * and line of sight themselves, so a forged packet through a wall or from across the map ends nothing. Ray and
 * projectile entries are nearest-wins ({@link MagicianPuppetRaycast}): a puppet absorbs a hit only when strictly
 * nearer than the player the weapon would hit and than any Seeker device it would break; blasts are area effects.
 * 武器命中魔术师皮套时将其结束的服务端入口，每种伤害来源一个入口，与搜寻者设备命中（{@code SeekerDeviceHits}）一致。
 * 所有被接受的命中都汇入 {@link MagicianPlaybackManager#endPuppet}，由它独自留下诱饵、向魔术师付款并记录回放。
 * 所有者 2026-10-07 决定：命中皮套与真实命中付出相同代价（D3：枪声、枪口火光、德林加已用与冷却；刀的冷却），
 * 但绝不触发误杀惩罚或理智损失；魔术师不能结束自己的皮套；旁观者永远命中不了。由客户端选目标的入口（枪、刀、猎魔枪、
 * 剑鱼、左键近战）自行校验武器、距离、瞄准与视线，因此隔墙或跨图伪造的数据包结束不了任何皮套。射线与投射物入口执行最近者
 * 命中（{@link MagicianPuppetRaycast}）：只有严格近于武器本会命中的玩家、也近于其本会打坏的搜寻者设备时，皮套才承受命中；
 * 爆炸属于范围效果。
 */
public final class MagicianPuppetHits {
    /**
     * Server melee reach for left-click weapons: vanilla interaction range 3 plus its 1-block attack allowance (the
     * Seeker's {@code SeekerDeviceHits.MELEE_REACH}). / 左键武器的服务端近战距离：原版交互距离 3 加 1 格攻击余量。
     */
    static final double MELEE_REACH = 4.0;
    /**
     * Player box growth of the Hunter shotgun pick ({@code DoubleBarrelShotgunItem.TARGET_BOX_EXPANSION}), given to the
     * puppet too. / Hunter 猎枪选取玩家时的箱体扩大量（皮套相同）。
     */
    static final double SHOTGUN_BOX_EXPANSION = 0.1;
    /**
     * Vanilla projectile entity margin ({@code ProjectileUtil.getEntityCollision}, 0.3F). / 原版投射物实体余量。
     */
    static final double PROJECTILE_MARGIN = 0.3;
    /**
     * SparkStrength M67 kill radius ({@code M67Rules.BLAST_RADIUS}: 5.0 since SparkStrength 3fa84e9, 2026-10-03).
     * SparkStrength M67 击杀半径（自 SparkStrength 3fa84e9 起为 5.0）。
     */
    public static final double M67_RADIUS = 5.0;
    /** SparkStrength M67 item name for the replay. / 回放用的 SparkStrength M67 物品名。 */
    static final String M67_ITEM_KEY = "item.sparkstrength.m67";
    /** Replay name of the Murderous Witch Death Ray (a skill, no item). / 杀意魔女死光的回放名（技能，无物品）。 */
    static final String DEATH_RAY_WEAPON_KEY = "replay.sparkwitch.magician.weapon.death_ray";
    /**
     * Server thread only: shooters owed NoellesRoles' Jester refund by the Demon Hunter packet being received (set by
     * the lookup, paid and cleared at its TAIL, cleared again by the shooter's next lookup).
     * 仅服务端线程：正在处理的猎魔枪数据包所欠小丑返还子弹的射手（由查找写入，在 TAIL 支付并清除，射手下一次查找时再次清除）。
     */
    private static final Set<UUID> JESTER_REFUNDS = new HashSet<>();

    private MagicianPuppetHits() {
    }

    /**
     * Wathe revolver/derringer receiver, called at its {@code recordItemUse} anchor (after Wathe's spectator, gun-tag,
     * cooldown and spent-derringer checks and after SparkTraits' Last Escape HEAD cancel). A puppet id never resolves to
     * Wathe's {@code ServerPlayerEntity} target, so Wathe then finishes the shot as a miss: sounds, muzzle flash, ammo
     * and cooldown, no punishment, no mood loss (D3). Validates revolver or derringer in the main hand, a live foreign
     * puppet, distance below Wathe's 65 cap, aim and line of sight. Returns true when the puppet ended.
     * Wathe 左轮/德林加接收器，在其 {@code recordItemUse} 锚点调用（位于 Wathe 的旁观、枪械标签、冷却与德林加已用检查之后，
     * 也在 SparkTraits 最后逃脱的 HEAD 取消之后）。皮套 id 永远不会解析为 Wathe 的 {@code ServerPlayerEntity} 目标，
     * 因此 Wathe 随后按未命中收尾：有声音、枪口火光、耗弹与冷却，没有惩罚也不扣理智（D3）。校验主手为左轮或德林加、
     * 他人的活皮套、距离低于 65、瞄准与视线。皮套被结束时返回 true。
     */
    public static boolean onGunPayload(ServerPlayerEntity shooter, @Nullable Entity target, ItemStack gun) {
        if (shooter == null || shooter.getWorld().isClient() || shooter.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet) || gun == null
                || !(gun.isOf(WatheItems.REVOLVER) || gun.isOf(WatheItems.DERRINGER))
                || !hittableBy(shooter, puppet)) {
            return false;
        }
        double max = SeekerDamageRules.GUN_MAX_DISTANCE;
        Box box = puppet.getBoundingBox();
        if (shooter.distanceTo(puppet) >= max
                || !SeekerDamageRules.gunAimedAndVisible(shooter.getWorld(), shooter.getEyePos(),
                shooter.getRotationVec(1.0F), max, box, box.expand(puppet.getTargetingMargin()), shooter)) {
            return false;
        }
        return MagicianPlaybackManager.endPuppet(puppet, shooter, GameConstants.DeathReasons.GUN,
                gun.getTranslationKey());
    }

    /**
     * Wathe knife charged-stab receiver, called at its HEAD after SparkTraits' priority-2200 guards (Last Escape, forced
     * melee cooldown, raised-knife release). Wathe returns at once for a non-player id, so the caller cancels after a
     * puppet stab. Validates the Wathe knife in either hand, no knife cooldown, the SparkTraits weapon-action gate, a
     * live foreign puppet, reach (3 + 0.5 latency) and line of sight. An accepted stab costs Wathe's own knife cooldown
     * (D3; none in creative or Loose Ends) and plays the stab sound. Returns true when the puppet ended.
     * Wathe 刀蓄力刺击接收器，在其 HEAD 处、SparkTraits 优先级 2200 的守卫（最后逃脱、强制近战冷却、举刀释放）之后调用。
     * Wathe 遇到非玩家 id 会立即返回，因此刺中皮套后由调用方取消。校验任一手持 Wathe 刀、刀未冷却、SparkTraits 武器动作门槛、
     * 他人的活皮套、距离（3 + 0.5 延迟）与视线。被接受的刺击付出 Wathe 自身的刀冷却（D3；创造模式或亡命徒模式没有），
     * 并播放刺击声。皮套被结束时返回 true。
     */
    public static boolean onKnifeStabPayload(ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || attacker.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet) || !hittableBy(attacker, puppet)) {
            return false;
        }
        ItemStack knife = heldKnife(attacker);
        if (knife.isEmpty() || attacker.getItemCooldownManager().isCoolingDown(knife.getItem())
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, knife)) {
            return false;
        }
        double reach = SeekerDamageRules.KNIFE_REACH + SeekerDamageRules.KNIFE_REACH_TOLERANCE;
        Vec3d eye = attacker.getEyePos();
        if (SeekerDamageRules.squaredDistanceToBox(eye, puppet.getBoundingBox()) > reach * reach
                || !SeekerDamageRules.hasLineOfSight(attacker.getWorld(), eye, puppet.getBoundingBox(), attacker)) {
            return false;
        }
        Vec3d at = puppet.getPos();
        if (!MagicianPlaybackManager.endPuppet(puppet, attacker, GameConstants.DeathReasons.KNIFE,
                knife.getTranslationKey())) {
            return false;
        }
        attacker.getServerWorld().playSound(null, at.x, at.y + 1.0, at.z, WatheSounds.ITEM_KNIFE_STAB,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        attacker.swingHand(attacker.getMainHandStack() == knife ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
        applyKnifeCooldown(attacker);
        return true;
    }

    /**
     * Area weapons: ends every live puppet whose box centre lies in the sphere with a clear line of sight to the
     * centre, never the thrower's own puppet. {@code thrower} may be null or offline (no reward then). Players are
     * killed by the weapon's own code as before.
     * 范围武器：结束所有碰撞箱中心位于球内且与爆心视线畅通的活皮套，从不结束投掷者自己的皮套。{@code thrower} 可为 null
     * 或已离线（此时没有奖励）。玩家仍由武器自身代码照常击杀。
     */
    public static void onBlast(ServerWorld world, Vec3d center, double radius, @Nullable ServerPlayerEntity thrower,
                               Identifier deathReason, @Nullable String weaponName) {
        if (world == null || center == null || deathReason == null || !(radius > 0.0)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        List<MagicianPlaybackEntity> hit = new ArrayList<>();
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            Box box = puppet.getBoundingBox();
            if (!MagicianPlaybackManager.isOwnedBy(puppet, thrower)
                    && SeekerDamageRules.inBlastSphere(center, box.getCenter(), radius)
                    && SeekerDamageRules.hasLineOfSight(world, center, box, null)) {
                hit.add(puppet);
            }
        }
        for (MagicianPlaybackEntity puppet : hit) {
            MagicianPlaybackManager.endPuppet(puppet, thrower, deathReason, weaponName);
        }
    }

    /**
     * SparkWitch Hunter double-barrel shotgun (server branch, after the shell is spent): nearest-wins along the shooter's
     * block-clipped ray of {@code range}. A puppet whose box (grown like the player pick, 0.1) is entered strictly
     * nearer than {@code target} and than every Seeker device the shooter may break ends ({@code GUN}); true means
     * "absorbed": the caller kills nobody and skips the Seeker hook, and the shot still costs its shell, sound and
     * cooldown (D3). Returns false when no puppet is involved.
     * SparkWitch 双管猎枪（服务端分支、耗弹之后）：沿射手按方块截断、长 {@code range} 的射线执行最近者命中。箱体（与玩家选取
     * 同样扩大 0.1）入射严格近于 {@code target} 且近于射手可打坏的所有搜寻者设备的皮套被结束（{@code GUN}）；true 表示
     * “已吸收”：调用方不击杀任何人并跳过搜寻者钩子，这一枪照常消耗弹壳、播放声音并进入冷却（D3）。不涉及皮套时返回 false。
     */
    public static boolean onShotgunFired(PlayerEntity shooter, ItemStack shotgun, @Nullable PlayerEntity target,
                                         double range) {
        if (!(shooter instanceof ServerPlayerEntity serverShooter) || shooter.getWorld().isClient()
                || !(range > 0.0) || !GameWorldComponent.KEY.get(shooter.getWorld()).isRunning()) {
            return false;
        }
        Vec3d start = serverShooter.getEyePos();
        Vec3d end = MagicianPuppetRaycast.clipToBlocks(serverShooter.getWorld(), start,
                start.add(serverShooter.getRotationVec(1.0F).multiply(range)), serverShooter);
        double beat = Math.min(
                MagicianPuppetRaycast.playerDistanceSquared(start, end, target, SHOTGUN_BOX_EXPANSION),
                MagicianPuppetRaycast.breakableDeviceSquared(serverShooter, start, end));
        MagicianPuppetRaycast.PuppetHit hit = MagicianPuppetRaycast.nearestHittable(serverShooter, start, end,
                SHOTGUN_BOX_EXPANSION, beat);
        return hit != null && MagicianPlaybackManager.endPuppet(hit.puppet(), serverShooter,
                GameConstants.DeathReasons.GUN, shotgun == null ? null : shotgun.getTranslationKey());
    }

    /**
     * NoellesRoles Demon Hunter pistol receiver, called at its single target lookup (after NoellesRoles' spectator,
     * held-pistol, cooldown and bullet checks). A puppet id never resolves to its player target, so NoellesRoles then
     * finishes the shot as a miss: bullet spent, sound, muzzle flash, cooldown (D3). Validates the pistol in the main
     * hand, no cooldown, a bullet, a live foreign puppet below the 65 cap, aim and line of sight like the Wathe guns.
     * The pistol kills only a non-stasis Jester or a player in frenzy, so the puppet ends ({@code demon_hunter}) only
     * when the copied player is one of those; any other "hit" changes nothing, exactly as on that player. A Jester
     * puppet also earns NoellesRoles' bullet refund, paid by {@link #afterDemonHunterShot} once NoellesRoles has spent
     * the bullet (it spends from a count read before this lookup). True = ended.
     * NoellesRoles 猎魔枪接收器，在其唯一一次目标查找处调用（位于 NoellesRoles 的旁观、手持、冷却与子弹检查之后）。皮套 id
     * 永远不会解析为其玩家目标，因此 NoellesRoles 随后按未命中收尾：消耗子弹、声音、枪口火光、冷却（D3）。校验主手猎魔枪、
     * 未冷却、有子弹、65 以内的他人活皮套，以及与 Wathe 枪械相同的瞄准与视线。猎魔枪只击杀非禁锢小丑或疯魔中的玩家，因此只有
     * 被复制的玩家属于其中之一时皮套才结束（{@code demon_hunter}）；其余“命中”与打在该玩家身上一样毫无变化。小丑皮套还会获得
     * NoellesRoles 的返还子弹，由 {@link #afterDemonHunterShot} 在 NoellesRoles 扣除子弹之后补上（它按本次查找之前读取的数量
     * 扣除）。返回 true 表示已结束。
     */
    public static boolean onDemonHunterPayload(ServerPlayerEntity shooter, @Nullable Entity target) {
        if (shooter != null) {
            JESTER_REFUNDS.remove(shooter.getUuid());
        }
        if (shooter == null || shooter.getWorld().isClient() || shooter.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet) || !hittableBy(shooter, puppet)) {
            return false;
        }
        ItemStack pistol = shooter.getMainHandStack();
        int bullets = pistol.getOrDefault(ModItems.BULLETS, 0);
        if (!pistol.isOf(ModItems.DEMON_HUNTER_PISTOL)
                || shooter.getItemCooldownManager().isCoolingDown(pistol.getItem()) || bullets <= 0) {
            return false;
        }
        double max = SeekerDamageRules.GUN_MAX_DISTANCE;
        Box box = puppet.getBoundingBox();
        if (shooter.distanceTo(puppet) >= max
                || !SeekerDamageRules.gunAimedAndVisible(shooter.getWorld(), shooter.getEyePos(),
                shooter.getRotationVec(1.0F), max, box, box.expand(puppet.getTargetingMargin()), shooter)) {
            return false;
        }
        // NoellesRoles judges the resolved player (online, not spectating); the puppet is judged as its copied player.
        // NoellesRoles 判定解析出的玩家（在线、非旁观）；皮套按其复制的玩家判定。
        ServerPlayerEntity copied = puppet.disguise() == null || shooter.getServer() == null
                ? null : shooter.getServer().getPlayerManager().getPlayer(puppet.disguise());
        DemonHunterVerdict verdict = DemonHunterVerdict.MISS;
        if (copied != null && !copied.isSpectator()) {
            boolean jester = GameWorldComponent.KEY.get(shooter.getWorld()).isRole(copied, Noellesroles.JESTER);
            verdict = demonHunterVerdict(jester, jester && JesterPlayerComponent.KEY.get(copied).inStasis,
                    PlayerPsychoComponent.KEY.get(copied).getPsychoTicks() > 0);
        }
        if (verdict == DemonHunterVerdict.MISS || !MagicianPlaybackManager.endPuppet(puppet, shooter,
                Noellesroles.DEATH_REASON_DEMON_HUNTER, pistol.getTranslationKey())) {
            return false;
        }
        if (verdict == DemonHunterVerdict.JESTER_KILL) {
            JESTER_REFUNDS.add(shooter.getUuid());
        }
        return true;
    }

    /**
     * Demon Hunter receiver TAIL (after NoellesRoles spent the bullet, possibly removing the empty pistol): pays the
     * Jester refund {@link #onDemonHunterPayload} owed for this very shot, exactly like NoellesRoles' own Jester branch
     * (one bullet back on the carried pistol, else a fresh pistol with one bullet in a free slot). No-op otherwise.
     * 猎魔枪接收器 TAIL（NoellesRoles 已扣除子弹、可能已移除打空的手枪之后）：支付 {@link #onDemonHunterPayload} 为这一枪记下的
     * 小丑返还子弹，与 NoellesRoles 自身的小丑分支完全一致（随身手枪补一发，否则在空位放入一把带一发子弹的新手枪）。否则无操作。
     */
    public static void afterDemonHunterShot(ServerPlayerEntity shooter) {
        if (shooter == null || !JESTER_REFUNDS.remove(shooter.getUuid())) {
            return;
        }
        ItemStack pistol = DemonHunterPistolItem.findPistol(shooter);
        if (pistol != null) {
            pistol.set(ModItems.BULLETS, pistol.getOrDefault(ModItems.BULLETS, 0) + 1);
        } else {
            ItemStack fresh = new ItemStack(ModItems.DEMON_HUNTER_PISTOL);
            fresh.set(ModItems.BULLETS, 1);
            ShopEntry.insertStackInFreeSlot(shooter, fresh);
        }
    }

    /**
     * Murderous Witch Death Ray: a piercing ray, so every live foreign puppet on the visible ray ends
     * ({@code pierced_by_ray}); puppets never cut the ray, exactly like players. Call after the Seeker cut, with the
     * visible distance it returned (players and puppets behind a broken device are spared alike). Uses the ray's own
     * player box growth ({@link MurderousWitchDeathRayRules#intersectsRay}).
     * 杀意魔女死光：穿透射线，因此可见射线上所有他人活皮套都会结束（{@code pierced_by_ray}）；皮套与玩家一样从不截断射线。
     * 在搜寻者截断之后调用，传入其返回的可见距离（被打坏设备之后的玩家与皮套同样幸免）。使用射线自身对玩家的箱体扩大量。
     */
    public static void onDeathRayFired(ServerPlayerEntity caster, Vec3d start, Vec3d direction,
                                       double visibleDistance) {
        if (caster == null || start == null || direction == null || !(visibleDistance > 0.0)
                || !(caster.getWorld() instanceof ServerWorld world)) {
            return;
        }
        List<MagicianPlaybackEntity> hit = new ArrayList<>();
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            if (hittableBy(caster, puppet) && MurderousWitchDeathRayRules.intersectsRay(start, direction,
                    puppet.getBoundingBox(), visibleDistance)) {
                hit.add(puppet);
            }
        }
        for (MagicianPlaybackEntity puppet : hit) {
            MagicianPlaybackManager.endPuppet(puppet, caster, SparkWitchDeathReasons.PIERCED_BY_RAY,
                    DEATH_RAY_WEAPON_KEY);
        }
    }

    /**
     * Ninja shuriken {@code canHit} filter: a live puppet stops the shuriken only when its thrower may end it, so the
     * Magician's own puppet (and any puppet for a spectating thrower) is fully transparent. Non-puppets and the client
     * (which never learns the owner) keep vanilla behaviour.
     * 忍者手里剑的 {@code canHit} 过滤：只有投掷者可结束的活皮套才会挡下手里剑，因此魔术师自己的皮套（以及旁观投掷者面前的
     * 任何皮套）完全透明。非皮套实体与客户端（无从得知主人）保持原版行为。
     */
    public static boolean projectileMayHit(@Nullable Entity thrower, Entity target) {
        if (!(target instanceof MagicianPlaybackEntity puppet) || target.getWorld().isClient()) {
            return true;
        }
        return thrower instanceof PlayerEntity player && hittableBy(player, puppet);
    }

    /**
     * Ninja shuriken entity hit (vanilla collision already picked the nearest entity, puppets included): a puppet its
     * thrower may end ends ({@code ninja_shuriken_kill}) with the player hit's crit burst and chain sound; true means
     * the caller discards the shuriken, as after a player kill.
     * 忍者手里剑命中实体（原版碰撞已在含皮套的实体中选出最近者）：投掷者可结束的皮套被结束（{@code ninja_shuriken_kill}），
     * 并播放与命中玩家相同的暴击粒子与锁链声；返回 true 时由调用方移除手里剑，与击杀玩家后一致。
     */
    public static boolean onShurikenHit(Entity shuriken, @Nullable Entity thrower, EntityHitResult hit,
                                        ItemStack weapon) {
        if (shuriken == null || hit == null || !(shuriken.getWorld() instanceof ServerWorld world)
                || !(hit.getEntity() instanceof MagicianPlaybackEntity puppet)
                || !(thrower instanceof ServerPlayerEntity attacker) || !hittableBy(attacker, puppet)) {
            return false;
        }
        Vec3d at = hit.getPos();
        if (!MagicianPlaybackManager.endPuppet(puppet, attacker, SparkWitchDeathReasons.NINJA_SHURIKEN_KILL,
                weapon == null ? null : weapon.getTranslationKey())) {
            return false;
        }
        world.spawnParticles(ParticleTypes.CRIT, at.x, at.y + 1.25, at.z, 10, 0.3, 0.3, 0.3, 0.15);
        world.playSound(null, shuriken.getX(), shuriken.getY(), shuriken.getZ(), SoundEvents.BLOCK_CHAIN_HIT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Read-only tick-head check for a projectile whose Seeker sweep runs before vanilla collision (Ninja shuriken):
     * true when a puppet its thrower may end lies on this tick's block-clipped segment (box grown by the vanilla 0.3
     * projectile margin) strictly before every Seeker device the thrower may break. The caller then skips the Seeker
     * sweep this tick, so vanilla collision takes it (the puppet, or a still nearer player). Ends nothing itself.
     * 供“搜寻者扫掠先于原版碰撞”的投射物（忍者手里剑）在 tick 开头做的只读检查：投掷者可结束的皮套位于本刻按方块截断的线段上
     * （箱体按原版 0.3 投射物余量扩大），且严格先于投掷者可打坏的所有搜寻者设备时返回 true。调用方随即跳过本刻的搜寻者扫掠，
     * 交由原版碰撞处理（命中该皮套或更近的玩家）。本方法自身不结束任何皮套。
     */
    public static boolean puppetBeforeDevice(Entity projectile, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        if (projectile == null || from == null || to == null || !(projectile.getWorld() instanceof ServerWorld world)
                || !(thrower instanceof ServerPlayerEntity attacker)
                || MagicianPlaybackManager.livePuppets(world).isEmpty()) {
            return false;
        }
        Vec3d end = MagicianPuppetRaycast.clipToBlocks(world, from, to, projectile);
        double device = MagicianPuppetRaycast.breakableDeviceSquared(attacker, from, end);
        return device != Double.POSITIVE_INFINITY
                && MagicianPuppetRaycast.nearestHittable(attacker, from, end, PROJECTILE_MARGIN, device) != null;
    }

    /**
     * NoellesRoles throwing axe, in its server pierce branch before the player loop: the axe pierces players, so every
     * live foreign puppet on this tick's segment ends ({@code throwing_axe}) with the player hit's trident sound and
     * 0.9 slow-down, and the axe flies on. Puppets use NoellesRoles' own player test (box grown by the targeting
     * margin, no block clip); {@code cutSquared} is the Seeker device cut, and a puppet behind it is spared like a
     * player (a tie goes to the puppet, as to a player).
     * NoellesRoles 飞斧，在其服务端贯穿分支、玩家循环之前：飞斧贯穿玩家，因此本刻线段上所有他人活皮套都会结束
     * （{@code throwing_axe}），并播放与命中玩家相同的三叉戟声、减速至 0.9，飞斧继续飞行。皮套沿用 NoellesRoles 自身的玩家
     * 判定（按瞄准余量扩大的箱体，不按方块截断）；{@code cutSquared} 为搜寻者设备截断距离，其后的皮套与玩家一样幸免
     * （距离相同时与玩家一样归皮套）。
     */
    public static void onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from, Vec3d to,
                                          double cutSquared) {
        if (axe == null || from == null || to == null || !(axe.getWorld() instanceof ServerWorld world)
                || !(thrower instanceof ServerPlayerEntity attacker)) {
            return;
        }
        List<MagicianPlaybackEntity> pierced = new ArrayList<>();
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            if (!hittableBy(attacker, puppet) || !puppet.canBeHitByProjectile()) {
                continue;
            }
            Optional<Vec3d> entry = puppet.getBoundingBox().expand(puppet.getTargetingMargin()).raycast(from, to);
            if (entry.isPresent()
                    && !SeekerDeviceRaycast.deviceWins(cutSquared, from.squaredDistanceTo(entry.get()))) {
                pierced.add(puppet);
            }
        }
        for (MagicianPlaybackEntity puppet : pierced) {
            if (MagicianPlaybackManager.endPuppet(puppet, attacker, Noellesroles.DEATH_REASON_THROWING_AXE,
                    ModItems.THROWING_AXE.getTranslationKey())) {
                axe.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 1.0F);
                axe.setVelocity(axe.getVelocity().multiply(0.9, 0.9, 0.9));
            }
        }
    }

    /**
     * Potion Gunner shell blast, after the Seeker blast and before the shell's own targets. Only the TR shell kills
     * (GW-DK/AC/MR only debuff players, so they leave a puppet untouched, as the copied player would stay standing);
     * a TR blast ends every live puppet the player rule would catch: feet in the N×N×N cube with a clear COLLIDER line
     * from the centre to the feet, body centre or eye ({@code potion_shell}). An offline gunner still ends puppets,
     * unpaid. In flight the shell already stops at a puppet through vanilla collision (any non-player living entity
     * stops it), so its burst lands on the puppet like on a player.
     * 药炮手炮弹爆炸，在搜寻者爆炸之后、炮弹自身目标判定之前。只有 TR 炮弹会击杀（GW-DK/AC/MR 只给玩家施加减益，因此不触及
     * 皮套，正如被复制的玩家仍会站着）；TR 爆炸结束玩家规则会波及的所有活皮套：脚下位于 N×N×N 立方体内，且爆心到其脚部、身体
     * 中心或眼睛至少一条 COLLIDER 线段无遮挡（{@code potion_shell}）。药炮手离线时照样结束皮套，但没有奖励。飞行中的炮弹已经
     * 通过原版碰撞停在皮套上（任何非玩家生物都会挡下它），因此爆炸与命中玩家时一样落在皮套上。
     */
    public static void onPotionShellBlast(ServerWorld world, Vec3d center, @Nullable PotionShellType type,
                                          @Nullable ServerPlayerEntity gunner, ItemStack shell) {
        if (world == null || center == null || type != PotionShellType.TR
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        int size = type.size();
        List<MagicianPlaybackEntity> hit = new ArrayList<>();
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            Box box = puppet.getBoundingBox();
            if (MagicianPlaybackManager.isOwnedBy(puppet, gunner)
                    || !PotionBlastRings.inCube(size, puppet.getX() - center.x, puppet.getZ() - center.z,
                    box.minY - center.y, box.maxY - center.y)) {
                continue;
            }
            for (Vec3d point : PotionBlastGeometry.sightPoints(puppet.getPos(), box, puppet.getEyeY())) {
                if (SeekerDamageRules.segmentClear(world, center, point, null)) {
                    hit.add(puppet);
                    break;
                }
            }
        }
        for (MagicianPlaybackEntity puppet : hit) {
            MagicianPlaybackManager.endPuppet(puppet, gunner, SparkWitchDeathReasons.POTION_SHELL,
                    shell == null ? null : shell.getTranslationKey());
        }
    }

    /**
     * Potion Gunner launcher backblast (one ordinary kill on the nearest player in the lane behind the gunner): the
     * nearest live foreign puppet in the lane, measured with the player rule ({@link PotionBackblastRules#hitDistance}
     * on its real box, clear line to {@link PotionBackblastRules#sightPoint}), takes the backblast instead when it is
     * strictly nearer than {@code reach} (the victim's distance, or the lane length without one; a tie goes to the
     * player) and than every Seeker device the gunner may break. True means absorbed: the caller skips the Seeker hook
     * and kills nobody ({@code potion_backblast}).
     * 药炮手炮筒尾焰（对药炮手身后通道内最近的一名玩家进行一次普通击杀）：通道内最近的他人活皮套按玩家规则量取（以真实碰撞箱
     * 计算 {@link PotionBackblastRules#hitDistance}，到 {@link PotionBackblastRules#sightPoint} 视线畅通），当其严格近于
     * {@code reach}（受害者距离，没有受害者时为通道长度；距离相同时归玩家）且近于药炮手可打坏的所有搜寻者设备时，改由它承受尾焰。
     * 返回 true 表示已被吸收：调用方跳过搜寻者钩子且不击杀任何人（{@code potion_backblast}）。
     */
    public static boolean onPotionBackblast(ServerPlayerEntity gunner, Vec3d origin, Vec3d backwards, double length,
                                            double reach) {
        if (gunner == null || origin == null || backwards == null || !(length > 0.0) || !(reach > 0.0)
                || !(gunner.getWorld() instanceof ServerWorld world) || !GameWorldComponent.KEY.get(world).isRunning()) {
            return false;
        }
        MagicianPlaybackEntity nearest = null;
        double nearestDistance = reach;
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            if (!hittableBy(gunner, puppet)) {
                continue;
            }
            Box box = puppet.getBoundingBox();
            double distance = PotionBackblastRules.hitDistance(origin, backwards, length, box);
            if (distance >= 0.0 && distance < nearestDistance && SeekerDamageRules.segmentClear(world, origin,
                    PotionBackblastRules.sightPoint(origin, backwards, distance, box), gunner)) {
                nearest = puppet;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return false;
        }
        Vec3d end = origin.add(backwards.normalize().multiply(reach));
        if (!MagicianPuppetRaycast.puppetWins(nearestDistance * nearestDistance,
                MagicianPuppetRaycast.breakableDeviceSquared(gunner, origin, end))) {
            return false;
        }
        return MagicianPlaybackManager.endPuppet(nearest, gunner, SparkWitchDeathReasons.POTION_BACKBLAST,
                SparkWitchItems.potionLauncher().getTranslationKey());
    }

    /**
     * SparkStrength M67 blast, from {@code SparkStrengthM67Compat} after its match-grenade gate (ACTIVE round, thrower
     * with a match role, fuse run out, thrower online): {@link #onBlast} with the M67's own kill radius and Wathe's
     * {@code grenade} reason, as the M67 uses for players.
     * SparkStrength M67 爆炸，由 {@code SparkStrengthM67Compat} 在其对局手雷门槛（对局 ACTIVE、投掷者持有对局职业、引信到时、
     * 投掷者在线）之后调用：以 M67 自身的击杀半径与 Wathe 的 {@code grenade} 死因（M67 对玩家使用的死因）调用 {@link #onBlast}。
     */
    public static void onM67Blast(ServerWorld world, Vec3d center, @Nullable ServerPlayerEntity thrower) {
        onBlast(world, center, M67_RADIUS, thrower, GameConstants.DeathReasons.GRENADE, M67_ITEM_KEY);
    }

    /**
     * Angler Swordfish stab, from the Swordfish receiver after its release, aim-ray and nearest-target checks resolved
     * {@code target} (the submitted puppet was the nearest candidate on the server's own ray). Repeats the main-hand
     * Swordfish, cooldown and SparkTraits weapon gates, reach (3 + 0.5 latency) and line of sight. True means accepted:
     * the caller consumes the Swordfish and plays the stab exactly as on a player hit (D3), with no friendly-fire
     * death ({@code swordfish_stab}).
     * 钓鱼佬剑鱼刺击，由剑鱼接收器在其松手、瞄准射线与最近目标检查得出 {@code target} 之后调用（提交的皮套是服务端自身射线上
     * 最近的候选）。重查主手剑鱼、冷却与 SparkTraits 武器门槛、距离（3 + 0.5 延迟）与视线。true 表示接纳：调用方与命中玩家时
     * 完全一样消耗剑鱼并播放刺击（D3），但没有误伤致死（{@code swordfish_stab}）。
     */
    public static boolean onSwordfishStab(ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || attacker.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet) || !hittableBy(attacker, puppet)) {
            return false;
        }
        ItemStack swordfish = attacker.getMainHandStack();
        if (!swordfish.isOf(SparkWitchItems.swordfish())
                || attacker.getItemCooldownManager().isCoolingDown(swordfish.getItem())
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, swordfish)) {
            return false;
        }
        double reach = FisherRules.SWORDFISH_REACH + SeekerDamageRules.KNIFE_REACH_TOLERANCE;
        Vec3d eye = attacker.getEyePos();
        if (SeekerDamageRules.squaredDistanceToBox(eye, puppet.getBoundingBox()) > reach * reach
                || !SeekerDamageRules.hasLineOfSight(attacker.getWorld(), eye, puppet.getBoundingBox(), attacker)) {
            return false;
        }
        return MagicianPlaybackManager.endPuppet(puppet, attacker, SparkWitchDeathReasons.SWORDFISH_STAB,
                swordfish.getTranslationKey());
    }

    /**
     * Wathe bat left-click ({@code MagicianPuppetAttackHandlers}, server): Wathe's own bat kill applies to player
     * targets only, so a puppet needs this entry. Like Wathe: the Wathe bat in the main hand at full attack charge
     * ({@code getAttackCooldownProgress(0.5F) >= 1}), then {@code bat} with Wathe's bat hit sound (3.0 at the eye) and
     * the attack charge reset (D3). Also requires the SparkTraits weapon gate, a live foreign puppet, and the melee
     * re-check every client-trusted entry makes (reach 3 + 1, line of sight). True = ended (cancel the attack).
     * Wathe 球棒左键（{@code MagicianPuppetAttackHandlers}，服务端）：Wathe 自身的球棒击杀只作用于玩家目标，因此皮套需要本入口。
     * 与 Wathe 一致：主手 Wathe 球棒且攻击蓄满（{@code getAttackCooldownProgress(0.5F) >= 1}），随后以 {@code bat} 结束，
     * 播放 Wathe 球棒命中声（眼部高度、音量 3.0）并重置攻击蓄力（D3）。另需 SparkTraits 武器门槛、他人活皮套，以及所有信任
     * 客户端的入口都会做的近战复查（距离 3 + 1、视线）。返回 true 表示已结束（取消这次攻击）。
     */
    public static boolean onBatHit(ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || attacker.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet)) {
            return false;
        }
        ItemStack bat = attacker.getMainHandStack();
        if (!bat.isOf(WatheItems.BAT) || attacker.getAttackCooldownProgress(0.5F) < 1.0F
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, bat)
                || !hittableBy(attacker, puppet) || !inMeleeReach(attacker, puppet)) {
            return false;
        }
        double x = puppet.getX();
        double eyeY = puppet.getEyeY();
        double z = puppet.getZ();
        if (!MagicianPlaybackManager.endPuppet(puppet, attacker, GameConstants.DeathReasons.BAT,
                bat.getTranslationKey())) {
            return false;
        }
        attacker.getServerWorld().playSound(null, x, eyeY, z, WatheSounds.ITEM_BAT_HIT, SoundCategory.PLAYERS,
                3.0F, 1.0F);
        attacker.resetLastAttackedTicks();
        return true;
    }

    /**
     * Grand Witch Ceremonial Sword left-click, from its attack handler on a fully charged strike (the handler already
     * applied the SparkTraits gate and reset the charge): a live foreign puppet in melee reach with line of sight takes
     * the strike as a sword kill ({@link #ceremonialSwordKill}). True = ended.
     * 大魔女仪礼剑左键，由其攻击处理器在蓄满的一击上调用（处理器已做过 SparkTraits 门槛并重置蓄力）：近战距离内且视线畅通的
     * 他人活皮套按一次剑杀承受这一击（{@link #ceremonialSwordKill}）。返回 true 表示已结束。
     */
    public static boolean onCeremonialSwordStrike(ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || attacker.isSpectator()
                || !(target instanceof MagicianPlaybackEntity puppet)
                || !attacker.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())
                || !hittableBy(attacker, puppet) || !inMeleeReach(attacker, puppet)) {
            return false;
        }
        return ceremonialSwordKill(attacker, puppet);
    }

    /**
     * Ceremonial Sword dash step, after the step's player contact check: a live foreign puppet whose box meets the
     * step's swept box is a dash contact like a player, so the dash stops there (true) and the puppet takes a sword
     * kill when one may be attempted now ({@link #ceremonialSwordKill}; on the kill cooldown the dash still stops at
     * it, as at a player). The nearest such puppet to the dasher is chosen.
     * 仪礼剑冲刺的每一步，在该步的玩家接触检查之后：碰撞箱与该步扫掠箱相交的他人活皮套与玩家一样构成冲刺接触，冲刺就此停下
     * （返回 true），且此刻可尝试剑杀时皮套承受一次剑杀（{@link #ceremonialSwordKill}；击杀冷却中冲刺照样停在它面前，
     * 与遇到玩家时相同）。选择离冲刺者最近的此类皮套。
     */
    public static boolean onCeremonialSwordDash(ServerPlayerEntity attacker, Box sweptBox) {
        if (attacker == null || sweptBox == null || !(attacker.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        MagicianPlaybackEntity contact = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (MagicianPlaybackEntity puppet : MagicianPlaybackManager.livePuppets(world)) {
            double distance = puppet.squaredDistanceTo(attacker);
            if (distance < nearest && hittableBy(attacker, puppet) && puppet.getBoundingBox().intersects(sweptBox)) {
                contact = puppet;
                nearest = distance;
            }
        }
        if (contact == null) {
            return false;
        }
        ceremonialSwordKill(attacker, contact);
        return true;
    }

    /**
     * Whether {@code entity} is a live puppet {@code attacker} may end (server; false on the client): the target
     * filter for receivers that list candidates themselves (the Swordfish aim ray).
     * {@code entity} 是否为 {@code attacker} 可结束的活皮套（服务端；客户端恒为 false）：供自行列出候选的接收器
     * （剑鱼瞄准射线）过滤目标。
     */
    public static boolean isHittablePuppet(@Nullable PlayerEntity attacker, @Nullable Entity entity) {
        return attacker != null && entity instanceof MagicianPlaybackEntity puppet && hittableBy(attacker, puppet);
    }

    /** Outcome of a Demon Hunter shot on the copied player. / 猎魔枪打在被复制玩家身上的结果。 */
    enum DemonHunterVerdict { MISS, KILL, JESTER_KILL }

    /**
     * NoellesRoles' Demon Hunter rule on the copied player, in its own order: a Jester is killed (with a bullet refund)
     * only outside stasis, whatever its frenzy; anyone else only in frenzy.
     * NoellesRoles 猎魔枪对被复制玩家的规则，按其自身顺序：小丑只在非禁锢时被击杀（并返还子弹），与疯魔无关；其他人只在疯魔中。
     */
    static DemonHunterVerdict demonHunterVerdict(boolean jester, boolean jesterInStasis, boolean frenzy) {
        if (jester) {
            return jesterInStasis ? DemonHunterVerdict.MISS : DemonHunterVerdict.JESTER_KILL;
        }
        return frenzy ? DemonHunterVerdict.KILL : DemonHunterVerdict.MISS;
    }

    // ---- Internal ----

    /**
     * Server melee re-check for a left-click on a puppet: vanilla checks reach only, so reach (3 + 1) and a COLLIDER
     * line of sight to any sample point are re-checked, like {@code SeekerDeviceHits.onMelee}.
     * 左键命中皮套的服务端近战复查：原版只检查距离，因此与 {@code SeekerDeviceHits.onMelee} 一样复查距离（3 + 1）与到任一
     * 采样点的 COLLIDER 视线。
     */
    private static boolean inMeleeReach(ServerPlayerEntity attacker, MagicianPlaybackEntity puppet) {
        Vec3d eye = attacker.getEyePos();
        return SeekerDamageRules.squaredDistanceToBox(eye, puppet.getBoundingBox()) <= MELEE_REACH * MELEE_REACH
                && SeekerDamageRules.hasLineOfSight(attacker.getWorld(), eye, puppet.getBoundingBox(), attacker);
    }

    /**
     * A Ceremonial Sword kill on a puppet, gated like {@code CeremonialSwordCombatService.killWithCeremonialSword} (no
     * sword kill cooldown, no strike in progress, the SparkTraits weapon gate) and paid like its completed kill: the
     * 30 s sword kill cooldown and the trident hit sound (D3; no record line, the forced end records the replay).
     * 对皮套的一次仪礼剑击杀，门槛与 {@code CeremonialSwordCombatService.killWithCeremonialSword} 相同（无剑杀冷却、无进行中的
     * 攻击、SparkTraits 武器门槛），代价与其完成击杀时相同：30 秒剑杀冷却与三叉戟命中声（D3；不写使用记录，由强制结束记录回放）。
     */
    private static boolean ceremonialSwordKill(ServerPlayerEntity attacker, MagicianPlaybackEntity puppet) {
        GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(attacker);
        ItemStack sword = new ItemStack(SparkWitchItems.ceremonialSword());
        if (!GrandWitchRules.canAttemptCeremonialSwordKill(runtime.getSwordKillCooldownTicks(),
                runtime.isSwordStrikeInProgress())
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, sword)) {
            return false;
        }
        Vec3d at = puppet.getPos();
        if (!MagicianPlaybackManager.endPuppet(puppet, attacker, SparkWitchDeathReasons.CEREMONIAL_BLADE,
                sword.getTranslationKey())) {
            return false;
        }
        runtime.setSwordKillCooldownTicks(GrandWitchRules.CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS);
        attacker.getServerWorld().playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_HIT,
                SoundCategory.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    /**
     * Live puppet that {@code attacker} may end: not its own (the Magician never ends its own puppet for a free decoy),
     * in the same world. / {@code attacker} 可结束的活皮套：不是自己的（魔术师不能为白得诱饵而结束自己的皮套），且在同一世界。
     */
    static boolean hittableBy(PlayerEntity attacker, MagicianPlaybackEntity puppet) {
        return MagicianPlaybackManager.isLive(puppet)
                && puppet.getWorld() == attacker.getWorld()
                && !MagicianPlaybackManager.isOwnedBy(puppet, attacker)
                && GameFunctions.isPlayerAliveAndSurvival(attacker);
    }

    /**
     * Wathe's own knife cooldown after an accepted stab ({@code KnifeStabPayload.Receiver}): the base minus 5 s per
     * player above the killer ratio, at least 10 s; none in creative or Loose Ends.
     * Wathe 自身在被接受的刺击后设置的刀冷却（{@code KnifeStabPayload.Receiver}）：基础值减去超出杀手比例的每名玩家 5 秒，
     * 至少 10 秒；创造模式或亡命徒模式没有冷却。
     */
    private static void applyKnifeCooldown(ServerPlayerEntity attacker) {
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (attacker.isCreative() || game.getGameMode() == WatheGameModes.LOOSE_ENDS) {
            return;
        }
        int totalPlayers = attacker.getServerWorld().getPlayers().size();
        int excessPlayers = Math.max(0, totalPlayers - game.getAllKillerTeamPlayers().size() * game.getKillerDividend());
        int baseCooldown = GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.KNIFE, 0);
        int cooldown = Math.max(GameConstants.getInTicks(0, 10),
                baseCooldown - excessPlayers * GameConstants.getInTicks(0, 5));
        attacker.getItemCooldownManager().set(WatheItems.KNIFE, cooldown);
    }

    /**
     * The Wathe knife itself in either hand (never a {@code KnifeItem} subclass, matching the SparkTraits knife gate).
     * 任一手持的 Wathe 刀本身（不含 {@code KnifeItem} 子类，与 SparkTraits 刀门槛一致）。
     */
    private static ItemStack heldKnife(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(WatheItems.KNIFE)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        return off.isOf(WatheItems.KNIFE) ? off : ItemStack.EMPTY;
    }
}
