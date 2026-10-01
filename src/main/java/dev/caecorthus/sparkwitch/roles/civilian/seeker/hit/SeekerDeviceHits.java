package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Frozen contract: one entry per damage source (owner decision Q4); every accepted hit ends in
 * {@code SeekerDeviceService.breakDevice(device, source, breaker)}, which alone ends the session, records replay and
 * calls {@code SeekerMarkService} (entries here never mark directly). Ray and projectile entries implement nearest-wins
 * blocking: a {@code T target} return value means "hit the player as before", {@code null} means "a nearer device took
 * the hit, do not hit the player". A device hit costs ammo/cooldown like a Wathe miss, with no innocent-shot penalty.
 * Every entry is a server-side no-op on the client and returns its input unchanged when no device is involved.
 * Every entry validates with {@link SeekerDamageRules#mayBreak} (owner as proxy target); a device the attacker may
 * not break is transparent to that attacker's server rays and sweeps (it neither breaks nor shields). Client-targeted
 * entries (revolver, derringer, Demon Hunter pistol, knife stab) receive the device id from the client, so a refused
 * break there resolves as a miss (see {@link SeekerDamageRules}). Blasts are area effects, never blocking.
 * 冻结契约：每个伤害来源一个入口（所有者决定 Q4）；每次被接受的命中都汇入 {@code SeekerDeviceService.breakDevice}，
 * 由它独自结束会话、记录回放并调用 {@code SeekerMarkService}（本类入口从不直接标记）。
 * 射线与投射物入口实现“最近者命中”：返回 {@code T target} 表示“照常命中玩家”，返回 {@code null} 表示
 * “更近的设备吸收了命中，不得命中玩家”。命中设备与 Wathe 未命中一样消耗弹药与冷却，但不会有误伤惩罚。
 * 客户端调用时均为空操作；不涉及设备时原样返回输入。所有入口都以 {@link SeekerDamageRules#mayBreak}（拥有者为代理目标）
 * 校验；攻击者不能打坏的设备对其服务端射线与扫掠透明（既不损坏也不挡枪）。由客户端选择目标的入口（左轮、德林加、猎魔枪、
 * 刀刺）从客户端收到设备 id，被拒绝时按未命中处理（见 {@link SeekerDamageRules}）。爆炸属于范围效果，从不遮挡。
 */
public final class SeekerDeviceHits {
    /** NoellesRoles Demon Hunter pistol, matched by registry id only. / 仅按注册 id 匹配的 NoellesRoles 猎魔枪。 */
    static final Identifier DEMON_HUNTER_PISTOL_ID = Identifier.of("noellesroles", "demon_hunter_pistol");
    /** NoellesRoles pistol bullet data component, by registry id. / NoellesRoles 猎魔枪子弹数据组件（注册 id）。 */
    static final Identifier DEMON_HUNTER_BULLETS_ID = Identifier.of("noellesroles", "bullets");
    /**
     * Server melee reach: vanilla entity interaction range 3 plus its own 1-block attack allowance.
     * 服务端近战距离：原版实体交互距离 3 加上其自身 1 格的攻击余量。
     */
    static final double MELEE_REACH = 4.0;

    private SeekerDeviceHits() {
    }

    /**
     * Bare hands and any left-click melee (AttackEntityCallback, phase seeker_device). Spectators PASS (vanilla
     * spectate), the owner and every other refused attacker FAIL (nothing happens), an allowed attacker breaks the
     * device (SUCCESS). Vanilla ({@code canInteractWithEntityIn}) checks reach only, never line of sight, so the server
     * re-checks reach (3 + 1, vanilla's own allowance) and COLLIDER line of sight to any sample point, like every other
     * client-trusted entry: a forged ATTACK packet through a wall breaks nothing.
     * 空手与任意左键近战。旁观者返回 PASS（原版观战），拥有者及其他被拒绝者返回 FAIL（无事发生），
     * 允许的攻击者打坏设备（SUCCESS）。原版只检查距离、不检查视线，因此服务端与其他信任客户端选择的入口一样，
     * 再次校验距离（3 + 1，原版自身的余量）与到任一采样点的 COLLIDER 视线：隔墙伪造的攻击包打不坏设备。
     */
    public static ActionResult onMelee(ServerPlayerEntity attacker, SeekerDeviceEntity device) {
        if (attacker == null || device == null || attacker.getWorld().isClient()) {
            return ActionResult.PASS;
        }
        if (attacker.isSpectator()) {
            return ActionResult.PASS;
        }
        if (!isLive(device) || SeekerDeviceRaycast.isOwnDevice(attacker, device)) {
            return ActionResult.FAIL;
        }
        Vec3d eye = attacker.getEyePos();
        if (device.getWorld() != attacker.getWorld()
                || SeekerDamageRules.squaredDistanceToBox(eye, SeekerDeviceRaycast.targetBox(device))
                > MELEE_REACH * MELEE_REACH
                || !SeekerDamageRules.hasLineOfSight(attacker.getWorld(), eye, device.getBoundingBox(), attacker)) {
            return ActionResult.FAIL;
        }
        if (!mayBreak(attacker, device)) {
            return ActionResult.FAIL;
        }
        SeekerDeviceService.breakDevice(device, SeekerBreakSource.MELEE, attacker);
        return ActionResult.SUCCESS;
    }

    /**
     * Wathe revolver/derringer receiver (called at its {@code recordItemUse} anchor, after Wathe's gun-tag, cooldown and
     * spent-derringer checks). Trusts the client's nearest-wins pick like Wathe does, but validates: revolver or
     * derringer in the main hand (a forged shotgun/Demon Hunter {@code gunshoot} never counts), live foreign device,
     * distance below Wathe's 65 cap, aim and line of sight ({@link SeekerDamageRules#gunAimedAndVisible}: the look ray
     * meets the margin-grown box with a clear segment to the device, or the 25° sample-cone fallback),
     * {@link SeekerDamageRules#mayBreak}.
     * Wathe then resolves the shot as a miss (target not a player): sound, ammo and cooldown, no punishment, no mood.
     * Returns true when a device broke.
     * Wathe 左轮/德林加接收器（在其 {@code recordItemUse} 锚点调用，位于 Wathe 的枪械标签、冷却与德林加已用检查之后）。
     * 与 Wathe 一样信任客户端的最近者选择，但会校验：主手为左轮或德林加（伪造的猎枪/猎魔枪 {@code gunshoot} 不算）、
     * 存活的他人设备、距离低于 65、瞄准与视线（{@link SeekerDamageRules#gunAimedAndVisible}：视线射线与扩大余量后的箱体相交
     * 且到设备的线段无遮挡，或 25° 采样锥兜底）、{@link SeekerDamageRules#mayBreak}。
     * 随后 Wathe 按未命中处理（目标不是玩家）：有声音、耗弹与冷却，没有惩罚也不扣理智。打坏设备时返回 true。
     */
    public static boolean onGunPayload(ServerPlayerEntity shooter, @Nullable Entity target, ItemStack gun) {
        if (shooter == null || shooter.getWorld().isClient() || !(target instanceof SeekerDeviceEntity device)
                || gun == null) {
            return false;
        }
        SeekerBreakSource source;
        if (gun.isOf(WatheItems.REVOLVER)) {
            source = SeekerBreakSource.REVOLVER;
        } else if (gun.isOf(WatheItems.DERRINGER)) {
            source = SeekerBreakSource.DERRINGER;
        } else {
            return false;
        }
        return breakTargeted(shooter, device, source, SeekerDamageRules.GUN_MAX_DISTANCE);
    }

    /**
     * SparkWitch double-barrel shotgun (one-line Hunter hook, server branch): nearest-wins along the shooter's 8-block
     * ray. A nearer breakable device breaks and the shot kills nobody (the Hunter's record then says {@code hit:false}).
     * SparkWitch 双管猎枪（Hunter 服务端分支中的一行钩子）：沿射手 8 格射线执行最近者命中；更近且可打坏的设备被打坏，
     * 这一枪不击杀任何人（Hunter 的记录随之为 {@code hit:false}）。
     */
    @Nullable
    public static <T extends PlayerEntity> T onShotgunFired(PlayerEntity shooter, @Nullable T target, double range) {
        if (!(shooter instanceof ServerPlayerEntity serverShooter) || shooter.getWorld().isClient()) {
            return target;
        }
        return rayBlocks(serverShooter, target, range, SeekerBreakSource.SHOTGUN) ? null : target;
    }

    /**
     * NoellesRoles Demon Hunter pistol receiver. Validates everything itself (pistol in the main hand, not cooling
     * down, at least one bullet, live foreign device within the receiver's 65 cap, the same aim and line-of-sight rule
     * as the Wathe guns, {@link SeekerDamageRules#mayBreak}) so it is correct at the receiver HEAD as well as at its
     * {@code recordItemUse} anchor. The caller lets NoellesRoles continue so the shot costs a bullet and cooldown as a
     * miss. True = broke.
     * NoellesRoles 猎魔枪接收器。所有条件自行校验（主手猎魔枪、未冷却、至少一发子弹、65 以内的存活他人设备、与 Wathe 枪械
     * 相同的瞄准与视线规则、{@link SeekerDamageRules#mayBreak}），因此在接收器 HEAD 或 {@code recordItemUse} 锚点调用都正确。
     * 调用方应让 NoellesRoles 继续执行，使这一枪按未命中消耗子弹与冷却。返回 true 表示已打坏。
     */
    public static boolean onDemonHunterPayload(ServerPlayerEntity shooter, @Nullable Entity target) {
        if (shooter == null || shooter.getWorld().isClient() || shooter.isSpectator()
                || !(target instanceof SeekerDeviceEntity device)) {
            return false;
        }
        ItemStack pistol = shooter.getMainHandStack();
        if (pistol.isEmpty() || !DEMON_HUNTER_PISTOL_ID.equals(Registries.ITEM.getId(pistol.getItem()))
                || shooter.getItemCooldownManager().isCoolingDown(pistol.getItem())
                || demonHunterBullets(pistol) <= 0) {
            return false;
        }
        return breakTargeted(shooter, device, SeekerBreakSource.DEMON_HUNTER_PISTOL,
                SeekerDamageRules.GUN_MAX_DISTANCE);
    }

    /**
     * Wathe knife right-click charged stab receiver. Validates the Wathe knife in either hand, no knife cooldown, the SparkTraits
     * weapon-action gate, reach (3 + 0.5 latency), line of sight and {@link SeekerDamageRules#mayBreak}. A device stab
     * costs nothing, like Wathe's miss (no cooldown, no Veteran stab use); returns true when a device broke.
     * Wathe 刀右键蓄力刺击接收器。校验任一手持 Wathe 刀、刀未冷却、SparkTraits 武器动作门槛、距离（3 + 0.5 延迟）、视线与
     * {@link SeekerDamageRules#mayBreak}。刺中设备与 Wathe 未刺中一样不产生代价（无冷却、不消耗老兵次数）；打坏设备时返回 true。
     */
    public static boolean onKnifeStabPayload(ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || attacker.isSpectator()
                || !(target instanceof SeekerDeviceEntity device)) {
            return false;
        }
        ItemStack knife = heldKnife(attacker);
        if (knife.isEmpty() || attacker.getItemCooldownManager().isCoolingDown(knife.getItem())
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, knife)) {
            return false;
        }
        double reach = SeekerDamageRules.KNIFE_REACH + SeekerDamageRules.KNIFE_REACH_TOLERANCE;
        if (!isLive(device) || SeekerDeviceRaycast.isOwnDevice(attacker, device)
                || SeekerDamageRules.squaredDistanceToBox(attacker.getEyePos(), SeekerDeviceRaycast.targetBox(device))
                > reach * reach
                || !SeekerDamageRules.hasLineOfSight(attacker.getWorld(), attacker.getEyePos(),
                device.getBoundingBox(), attacker)
                || !mayBreak(attacker, device)) {
            return false;
        }
        SeekerDeviceService.breakDevice(device, SeekerBreakSource.KNIFE_STAB, attacker);
        attacker.swingHand(attacker.getMainHandStack() == knife ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
        return true;
    }

    /** Control Expert taser server targeting; a device hit stuns nobody. / 控场专家电击枪；命中设备时不眩晕任何人。 */
    @Nullable
    public static <T extends PlayerEntity> T onTaserFired(ServerPlayerEntity user, @Nullable T target, double range) {
        if (user == null || user.getWorld().isClient()) {
            return target;
        }
        return rayBlocks(user, target, range, SeekerBreakSource.TASER) ? null : target;
    }

    /**
     * Murderous Witch death ray: a piercing multi-target ray, so nearest-wins means "cut the ray at the nearest
     * device". Breaks that device and returns the shortened visible distance (players before it are still hit, players
     * behind it are not); returns {@code visibleDistance} unchanged when no device lies on the ray. One-line hook:
     * {@code double visibleDistance = SeekerDeviceHits.onDeathRayFired(caster, start, direction,
     * visibleRayDistance(world, caster, start, direction));}
     * 杀意魔女死光：穿透多目标射线，因此“最近者命中”即“在最近的设备处截断射线”。打坏该设备并返回缩短后的可见距离
     * （设备前的玩家照常命中，其后的玩家不受影响）；射线上没有设备时原样返回 {@code visibleDistance}。
     */
    public static double onDeathRayFired(ServerPlayerEntity user, Vec3d start, Vec3d direction,
                                         double visibleDistance) {
        if (user == null || user.getWorld().isClient() || start == null || direction == null
                || !(visibleDistance > 0.0) || direction.lengthSquared() == 0.0) {
            return visibleDistance;
        }
        Vec3d end = start.add(direction.normalize().multiply(visibleDistance));
        SeekerDeviceRaycast.DeviceHit hit = SeekerDeviceRaycast.nearestDevice(user.getWorld(), start, end,
                Double.POSITIVE_INFINITY, breakableBy(user));
        if (hit == null) {
            return visibleDistance;
        }
        SeekerDeviceService.breakDevice(hit.device(), SeekerBreakSource.DEATH_RAY, user);
        return Math.min(visibleDistance, Math.sqrt(hit.distanceSquared()));
    }

    /**
     * Black Raven feather blade right-click (a server hitscan mark, not a projectile). One-line hook in
     * {@code FeatherBladeItem#use}: {@code ServerPlayerEntity target = SeekerDeviceHits.onFeatherBladeFired(serverUser,
     * aim == null ? null : aim.player(), BlackRavenRules.FEATHER_REACH);}
     * 黑鸦羽刃右键（服务端即时射线标记，并非投射物）。{@code FeatherBladeItem#use} 中的一行钩子。
     */
    @Nullable
    public static <T extends PlayerEntity> T onFeatherBladeFired(ServerPlayerEntity user, @Nullable T target,
                                                                 double range) {
        if (user == null || user.getWorld().isClient()) {
            return target;
        }
        return rayBlocks(user, target, range, SeekerBreakSource.FEATHER_BLADE) ? null : target;
    }

    /**
     * NoellesRoles throwing axe; true = a device was hit, stop as on an entity hit. The axe pierces players, so players
     * on the segment never shield a device from it. / 飞斧；true 表示命中设备。飞斧贯穿玩家，因此线段上的玩家不会替设备挡下。
     */
    public static boolean onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return sweep(axe, thrower, from, to, false, SeekerBreakSource.THROWING_AXE);
    }

    /**
     * Thrown ninja shuriken, checked at the top of {@code NinjaShurikenEntity#tick} from {@code getPos()} to
     * {@code getPos().add(getVelocity())}; the caller discards the projectile on true. A nearer living player on the
     * segment keeps the hit (vanilla collision resolves it this tick).
     * 投掷出的忍者手里剑，在 {@code NinjaShurikenEntity#tick} 开头检查；返回 true 时由调用方移除投射物。
     * 线段上更近的存活玩家保留这次命中（本刻由原版碰撞结算）。
     */
    public static boolean onShurikenSweep(Entity shuriken, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return sweep(shuriken, thrower, from, to, true, SeekerBreakSource.SHURIKEN);
    }

    /**
     * Control Expert thrown Shock Device; breaks a device it collides with (checked at the top of its tick like the
     * shuriken). The caller then lands the device at that point as on any collision. A nearer living player keeps the
     * collision; a player transparent to the Shock Device is passed next tick.
     * 控场专家投掷的电击装置；撞上设备即将其打坏（与手里剑一样在 tick 开头检查），随后调用方按普通碰撞在该点落地。
     * 更近的存活玩家保留这次碰撞；对电击装置透明的玩家会在下一刻被越过。
     */
    public static boolean onShockDeviceSweep(Entity shockDevice, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return sweep(shockDevice, thrower, from, to, true, SeekerBreakSource.SHOCK_DEVICE);
    }

    /**
     * Area weapons (Wathe grenade incl. Bomb Maniac, SparkStrength M67): break every device in the sphere with line of
     * sight to the centre; players are still killed as before (no blocking). With an online thrower each device is
     * gated by {@link SeekerDamageRules#mayBreak} (never the thrower's own); without one the break is unattributed.
     * 范围武器（Wathe 手雷含炸弹狂人、SparkStrength M67）：打坏球内与爆心有视线的所有设备；玩家照常死亡（不遮挡）。
     * 有在线投掷者时每个设备都经 {@link SeekerDamageRules#mayBreak} 校验（从不打坏投掷者自己的设备）；没有投掷者时损坏不归属任何人。
     */
    public static void onBlast(ServerWorld world, Vec3d center, double radius, @Nullable ServerPlayerEntity owner,
                               SeekerBreakSource source) {
        if (world == null || center == null || source == null || !(radius > 0.0)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        Box search = new Box(center, center).expand(radius + 1.0);
        List<SeekerDeviceEntity> hit = new ArrayList<>();
        for (SeekerDeviceEntity device : world.getEntitiesByClass(SeekerDeviceEntity.class, search,
                SeekerDeviceHits::isLive)) {
            Box box = device.getBoundingBox();
            if (SeekerDamageRules.inBlastSphere(center, box.getCenter(), radius)
                    && SeekerDamageRules.hasLineOfSight(world, center, box, null)
                    && (owner == null || (!SeekerDeviceRaycast.isOwnDevice(owner, device) && mayBreak(owner, device)))) {
                hit.add(device);
            }
        }
        // Collected first so breaking one device never changes the set seen by the rest. / 先收集，避免打坏过程影响其余判定。
        for (SeekerDeviceEntity device : hit) {
            if (isLive(device)) {
                SeekerDeviceService.breakDevice(device, source, owner);
            }
        }
    }

    // ---- Internal ----

    /**
     * Validated targeted break for a trusted client pick (Wathe gun / Demon Hunter): distance, then aim and line of
     * sight via {@link SeekerDamageRules#gunAimedAndVisible} (the look ray against the same margin-grown box the client
     * pick uses, with the sample cone as latency fallback), then {@link SeekerDamageRules#mayBreak}.
     * 对客户端选择的目标做校验后再打坏：距离，再经 {@link SeekerDamageRules#gunAimedAndVisible} 校验瞄准与视线
     * （视线射线对照客户端选择所用的同一扩大箱体，采样锥作为延迟兜底），最后 {@link SeekerDamageRules#mayBreak}。
     */
    private static boolean breakTargeted(ServerPlayerEntity shooter, SeekerDeviceEntity device,
                                         SeekerBreakSource source, double maxDistance) {
        if (!isLive(device) || SeekerDeviceRaycast.isOwnDevice(shooter, device)
                || device.getWorld() != shooter.getWorld()) {
            return false;
        }
        Vec3d eye = shooter.getEyePos();
        Box box = device.getBoundingBox();
        if (shooter.distanceTo(device) >= maxDistance
                || !SeekerDamageRules.gunAimedAndVisible(shooter.getWorld(), eye, shooter.getRotationVec(1.0F),
                maxDistance, box, SeekerDeviceRaycast.targetBox(device), shooter)
                || !mayBreak(shooter, device)) {
            return false;
        }
        SeekerDeviceService.breakDevice(device, source, shooter);
        return true;
    }

    /**
     * Server hitscan nearest-wins along the shooter's current aim ({@code getRotationVec(1)}, like the hooked items):
     * breaks the nearest breakable device strictly nearer than {@code target}. / 服务端即时射线的最近者命中。
     */
    private static boolean rayBlocks(ServerPlayerEntity shooter, @Nullable PlayerEntity target, double range,
                                     SeekerBreakSource source) {
        if (!(range > 0.0) || !GameWorldComponent.KEY.get(shooter.getWorld()).isRunning()) {
            return false;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d end = start.add(shooter.getRotationVec(1.0F).multiply(range));
        SeekerDeviceRaycast.DeviceHit hit = SeekerDeviceRaycast.blockingHit(shooter, start, end, target,
                breakableBy(shooter));
        if (hit == null) {
            return false;
        }
        SeekerDeviceService.breakDevice(hit.device(), source, shooter);
        return true;
    }

    /**
     * Projectile sweep with the thrower as breaker. An offline or non-player thrower breaks nothing (its projectile
     * kills nobody either). {@code playersBlock}: a nearer hittable player keeps the hit.
     * 以投掷者为损坏者的投射物扫掠。离线或非玩家投掷者不打坏任何设备（其投射物也不会击杀任何人）。
     */
    private static boolean sweep(Entity projectile, @Nullable Entity thrower, Vec3d from, Vec3d to,
                                 boolean playersBlock, SeekerBreakSource source) {
        if (projectile == null || from == null || to == null || projectile.getWorld().isClient()
                || !(thrower instanceof ServerPlayerEntity breaker)
                || !GameWorldComponent.KEY.get(projectile.getWorld()).isRunning()) {
            return false;
        }
        // Runs every projectile tick: skip the block raycast and player scan when no device is near the segment.
        // 每个投射物每刻都会执行：线段附近没有设备时跳过方块射线与玩家扫描。
        if (projectile.getWorld().getEntitiesByClass(SeekerDeviceEntity.class, new Box(from, to).expand(1.0),
                SeekerDeviceHits::isLive).isEmpty()) {
            return false;
        }
        Vec3d end = SeekerDeviceRaycast.clipToBlocks(projectile.getWorld(), from, to, projectile);
        double beat = playersBlock ? nearestPlayerDistanceSquared(projectile, breaker, from, end)
                : Double.POSITIVE_INFINITY;
        SeekerDeviceRaycast.DeviceHit hit = SeekerDeviceRaycast.nearestDevice(projectile.getWorld(), from, end,
                beat, breakableBy(breaker));
        if (hit == null) {
            return false;
        }
        SeekerDeviceService.breakDevice(hit.device(), source, breaker);
        return true;
    }

    private static double nearestPlayerDistanceSquared(Entity projectile, ServerPlayerEntity thrower, Vec3d from,
                                                       Vec3d end) {
        double nearest = Double.POSITIVE_INFINITY;
        Box search = new Box(from, end).expand(1.0);
        for (PlayerEntity player : projectile.getWorld().getEntitiesByClass(PlayerEntity.class, search,
                candidate -> !SeekerDamageRules.isSamePlayer(candidate, thrower)
                        && GameFunctions.isPlayerAliveAndSurvival(candidate)
                        && candidate.canBeHitByProjectile())) {
            Box box = player.getBoundingBox().expand(player.getTargetingMargin());
            double distance = SeekerDeviceRaycast.entryDistanceSquared(from, end, box);
            if (distance >= 0.0 && distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    private static Predicate<SeekerDeviceEntity> breakableBy(ServerPlayerEntity attacker) {
        return device -> !SeekerDeviceRaycast.isOwnDevice(attacker, device) && mayBreak(attacker, device);
    }

    private static boolean mayBreak(ServerPlayerEntity attacker, SeekerDeviceEntity device) {
        return SeekerDamageRules.mayBreak(attacker, SeekerDeviceService.findOwner(device),
                GameWorldComponent.KEY.get(attacker.getWorld()));
    }

    private static boolean isLive(SeekerDeviceEntity device) {
        return device != null && device.isAlive() && !device.isRemoved();
    }

    /**
     * The Wathe knife itself, never a {@code KnifeItem} subclass: the Vendetta knife has its own payload and never sends
     * a stab, so a forged stab while holding it must not break devices (matches the SparkTraits knife-packet gate).
     * 仅限 Wathe 刀本身，不含 {@code KnifeItem} 子类：复仇者之刀走自己的数据包、从不发送刺击，持有它时伪造的刺击不得打坏设备
     * （与 SparkTraits 刀数据包门槛一致）。
     */
    private static ItemStack heldKnife(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(WatheItems.KNIFE)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        return off.isOf(WatheItems.KNIFE) ? off : ItemStack.EMPTY;
    }

    private static int demonHunterBullets(ItemStack pistol) {
        var type = Registries.DATA_COMPONENT_TYPE.get(DEMON_HUNTER_BULLETS_ID);
        if (type == null) {
            return 0;
        }
        Object value = pistol.get(type);
        return value instanceof Integer bullets ? bullets : 0;
    }
}
