package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Server-only Holy Flash victim predicates. Unlike the Control Expert gate, the thrower IS flashed (owner decision
 * 2026-10-03) and only has to be a participant; everyone else must also pass SparkFactionAPI's structural veto for
 * {@link #ACTION_ID}, SparkTraits Last Escape and Vendetta isolation.
 * 仅服务端使用的圣光弹受害者判定。与控场专家不同，投掷者本人也会被闪（所有者 2026-10-03 决定），且只要求是参与者；
 * 其他人还必须通过 SparkFactionAPI 对 {@link #ACTION_ID} 的结构性否决、SparkTraits 最后逃脱与复仇者隔离。
 */
public final class HolyFlashTargeting {
    /** SparkFactionAPI action id for the flash. / 圣光弹在 SparkFactionAPI 中的动作 id。 */
    public static final Identifier ACTION_ID = HolyFlashRules.ITEM_ID;
    /** Half size of the head box whose sample points are tested for sight. / 视线检测所用头部箱体的半边长。 */
    static final double HEAD_HALF_SIZE = 0.25D;

    private HolyFlashTargeting() {
    }

    /**
     * With no live participant thrower (died or left mid-flight) there is no actor for SparkFactionAPI, so the flash
     * skips that veto, still honours Last Escape, and never reaches an active Vendetta endpoint.
     * 没有存活参与者投掷者（飞行途中死亡或离线）时 SparkFactionAPI 没有发起者，因此跳过该否决，
     * 仍遵守最后逃脱，且永不影响处于激活状态的复仇者一端。
     */
    public static boolean canFlash(@Nullable ServerPlayerEntity thrower, ServerPlayerEntity target) {
        if (thrower == null) {
            return affects(
                    false,
                    () -> ControlExpertTargeting.isParticipant(target),
                    () -> true,
                    () -> SparkTraitsKillerBridge.isLastEscapeActive(target),
                    () -> !VendettaInteractionService.isActiveVendetta(target));
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(thrower.getWorld());
        return affects(
                thrower == target || thrower.getUuid().equals(target.getUuid()),
                () -> ControlExpertTargeting.isParticipant(target),
                () -> SparkFactionApi.canAffectPlayer(thrower, target, ACTION_ID, game),
                () -> SparkTraitsKillerBridge.isLastEscapeActive(target),
                () -> vendettaAllows(thrower, target));
    }

    /**
     * Pure gate: the thrower needs only participation; later seams are consulted only after every earlier one passed.
     * 纯判定：投掷者本人只需是参与者；前序条件全部通过后才查询后续接缝。
     */
    static boolean affects(boolean self, BooleanSupplier participant, BooleanSupplier factionAllows,
                           BooleanSupplier lastEscape, BooleanSupplier vendettaAllows) {
        if (!participant.getAsBoolean()) {
            return false;
        }
        return self || factionAllows.getAsBoolean()
                && !lastEscape.getAsBoolean()
                && vendettaAllows.getAsBoolean();
    }

    /** Same Vendetta rule as the Control Expert's: an active endpoint is reachable only inside its exact pair. / 与控场专家相同的复仇者规则。 */
    static boolean vendettaAllows(PlayerEntity thrower, PlayerEntity target) {
        boolean throwerVendetta = VendettaInteractionService.isActiveVendetta(thrower);
        boolean targetVendetta = VendettaInteractionService.isActiveVendetta(target);
        return !(throwerVendetta || targetVendetta) || VendettaInteractionService.isExactPair(thrower, target);
    }

    /**
     * Walls block: at least one sample of a small box around the eyes must be visible from the burst through
     * COLLIDER shapes (Seeker's world line-of-sight helper, absent shape context as for a blast centre).
     * 墙体阻挡：从爆点经 COLLIDER 形状必须能看到眼部小箱体的至少一个采样点（复用搜寻者的世界视线工具，
     * 与爆心一样使用空形状上下文）。
     */
    public static boolean hasLineOfSight(World world, Vec3d burst, Vec3d eye) {
        Box head = new Box(eye, eye).expand(HEAD_HALF_SIZE);
        return SeekerDamageRules.hasLineOfSight(world, burst, head, null);
    }

    /**
     * Distance from the burst to the nearest point of {@code body} (the player's hitbox); 0 when the burst is inside
     * it. Sight and facing still use the eyes.
     * 爆点到 {@code body}（玩家碰撞箱）最近点的距离；爆点在箱体内时为 0。视线与正对判定仍以眼睛为准。
     */
    public static double bodyDistance(Box body, Vec3d burst) {
        double x = MathHelper.clamp(burst.x, body.minX, body.maxX);
        double y = MathHelper.clamp(burst.y, body.minY, body.maxY);
        double z = MathHelper.clamp(burst.z, body.minZ, body.maxZ);
        return burst.distanceTo(new Vec3d(x, y, z));
    }

    /**
     * Cosine between the look vector and the eye-to-burst direction; a burst inside the head counts as faced.
     * 视线与“眼睛到爆点”方向的夹角余弦；爆点在头部内时视为正对。
     */
    public static double facingCos(Vec3d look, Vec3d eye, Vec3d burst) {
        Vec3d toBurst = burst.subtract(eye);
        if (toBurst.lengthSquared() < 1.0E-6D || look.lengthSquared() < 1.0E-6D) {
            return 1.0D;
        }
        return look.normalize().dotProduct(toBurst.normalize());
    }
}
