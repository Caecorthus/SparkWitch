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
 * {@code SeekerDeviceService.breakDevice(device, source, breaker)}. Ray and projectile entries implement nearest-wins
 * blocking: a {@code T target} return value means "hit the player as before", {@code null} means "a nearer device took
 * the hit, do not hit the player". A device hit costs ammo/cooldown like a Wathe miss, with no innocent-shot penalty.
 * TODO(WP-04 core; WP-04b demon hunter/axe/SparkWitch thrown weapons). / 待 WP-04 与 WP-04b 实现。
 * 冻结契约：每个伤害来源一个入口（所有者决定 Q4）；每次被接受的命中都汇入 {@code SeekerDeviceService.breakDevice}。
 * 射线与投射物入口实现“最近者命中”：返回 {@code T target} 表示“照常命中玩家”，返回 {@code null} 表示
 * “更近的设备吸收了命中，不得命中玩家”。命中设备与 Wathe 未命中一样消耗弹药与冷却，但不会有误伤惩罚。
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

    /** Murderous Witch death ray. / 杀意魔女死光。 */
    @Nullable
    public static <T extends Entity> T onDeathRayFired(ServerPlayerEntity user, @Nullable T target, Vec3d start,
                                                       Vec3d end) {
        return target;
    }

    /** NoellesRoles throwing axe; true = a device was hit, stop as on an entity hit. / 飞斧；true 表示命中设备。 */
    public static boolean onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /** Thrown ninja shuriken. / 投掷出的忍者手里剑。 */
    public static boolean onShurikenSweep(Entity shuriken, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /** Black Raven thrown feather blade. / 黑鸦投掷出的羽刃。 */
    public static boolean onFeatherBladeSweep(Entity blade, @Nullable Entity thrower, Vec3d from, Vec3d to) {
        return false;
    }

    /** Control Expert thrown Shock Device; breaks a device it collides with. / 控场专家投掷的电击装置。 */
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
