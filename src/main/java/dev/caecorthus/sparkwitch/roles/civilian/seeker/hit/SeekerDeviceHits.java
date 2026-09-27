package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: one entry per damage source (owner decision Q4); every accepted hit ends in
 * {@code SeekerDeviceService.breakDevice(device, source, breaker)}, which alone ends the session, records replay and
 * calls {@code SeekerMarkService} (entries here never mark directly). Ray and projectile entries implement nearest-wins
 * blocking: a {@code T target} return value means "hit the player as before", {@code null} means "a nearer device took
 * the hit, do not hit the player". A device hit costs ammo/cooldown like a Wathe miss, with no innocent-shot penalty.
 * Every entry is a server-side no-op on the client and returns its input unchanged when no device is involved.
 * TODO(WP-04 core; WP-04b demon hunter/axe/SparkWitch hooks). / 待 WP-04 与 WP-04b 实现。
 * 冻结契约：每个伤害来源一个入口（所有者决定 Q4）；每次被接受的命中都汇入 {@code SeekerDeviceService.breakDevice}，
 * 由它独自结束会话、记录回放并调用 {@code SeekerMarkService}（本类入口从不直接标记）。
 * 射线与投射物入口实现“最近者命中”：返回 {@code T target} 表示“照常命中玩家”，返回 {@code null} 表示
 * “更近的设备吸收了命中，不得命中玩家”。命中设备与 Wathe 未命中一样消耗弹药与冷却，但不会有误伤惩罚。
 * 客户端调用时均为空操作；不涉及设备时原样返回输入。
 */
public final class SeekerDeviceHits {
    private SeekerDeviceHits() {
    }

    /** Bare hands and any left-click melee (AttackEntityCallback, phase seeker_device). / 空手与任意左键近战。 */
    public static ActionResult onMelee(ServerPlayerEntity attacker, SeekerDeviceEntity device) {
        return ActionResult.PASS;
    }

    /** Wathe revolver/derringer receiver: target id validated as a device. Returns true when a device broke. / 左轮与德林加。 */
    public static boolean onGunPayload(ServerPlayerEntity shooter, @Nullable Entity target, ItemStack gun) {
        return false;
    }

    /** SparkWitch double-barrel shotgun (one-line Hunter hook). / SparkWitch 双管猎枪（Hunter 文件中的一行钩子）。 */
    @Nullable
    public static <T extends PlayerEntity> T onShotgunFired(PlayerEntity shooter, @Nullable T target, double range) {
        return target;
    }

    /** NoellesRoles Demon Hunter pistol receiver. / NoellesRoles 猎魔枪接收器。 */
    public static boolean onDemonHunterPayload(ServerPlayerEntity shooter, @Nullable Entity target) {
        return false;
    }

    /** Wathe knife right-click charged stab receiver. / Wathe 刀的右键蓄力刺击接收器。 */
    public static boolean onKnifeStabPayload(ServerPlayerEntity attacker, @Nullable Entity target) {
        return false;
    }

    /** Control Expert taser server targeting; a device hit stuns nobody. / 控场专家电击枪；命中设备时不眩晕任何人。 */
    @Nullable
    public static <T extends PlayerEntity> T onTaserFired(ServerPlayerEntity user, @Nullable T target, double range) {
        return target;
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
        return visibleDistance;
    }

    /**
     * Black Raven feather blade right-click (a server hitscan mark, not a projectile). One-line hook in
     * {@code FeatherBladeItem#use}: {@code ServerPlayerEntity target = SeekerDeviceHits.onFeatherBladeFired(serverUser,
     * BlackRavenTargeting.findAimedPlayer(serverUser), BlackRavenRules.FEATHER_REACH);}
     * 黑鸦羽刃右键（服务端即时射线标记，并非投射物）。{@code FeatherBladeItem#use} 中的一行钩子。
     */
    @Nullable
    public static <T extends PlayerEntity> T onFeatherBladeFired(ServerPlayerEntity user, @Nullable T target,
                                                                 double range) {
        return target;
    }

    /** NoellesRoles throwing axe; true = a device was hit, stop as on an entity hit. / 飞斧；true 表示命中设备。 */
    public static boolean onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /**
     * Thrown ninja shuriken, checked at the top of {@code NinjaShurikenEntity#tick} from {@code getPos()} to
     * {@code getPos().add(getVelocity())}; the caller discards the projectile on true.
     * 投掷出的忍者手里剑，在 {@code NinjaShurikenEntity#tick} 开头检查；返回 true 时由调用方移除投射物。
     */
    public static boolean onShurikenSweep(Entity shuriken, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /**
     * Control Expert thrown Shock Device; breaks a device it collides with (checked at the top of its tick like the
     * shuriken). The caller then lands the device at that point as on any collision.
     * 控场专家投掷的电击装置；撞上设备即将其打坏（与手里剑一样在 tick 开头检查），随后调用方按普通碰撞在该点落地。
     */
    public static boolean onShockDeviceSweep(Entity shockDevice, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /**
     * Area weapons (Wathe grenade incl. Bomb Maniac, SparkStrength M67): break every device in the sphere with line of
     * sight to the centre; players are still killed as before (no blocking).
     * 范围武器（Wathe 手雷含炸弹狂人、SparkStrength M67）：打坏球内与爆心有视线的所有设备；玩家照常死亡（不遮挡）。
     */
    public static void onBlast(ServerWorld world, Vec3d center, double radius, @Nullable ServerPlayerEntity owner,
                               SeekerBreakSource source) {
    }
}
