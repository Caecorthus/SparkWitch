package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Server-only blast targeting, grenade rules plus line of sight. A candidate is a living round participant in
 * survival/adventure whose feet lie in the N×N×N cube ({@link PotionBlastRings#inCube}) with a clear COLLIDER segment
 * from the blast centre to its feet, body centre or eye (closed doors block). Factions use the target's real
 * SparkFactionAPI effective faction, never the Black Raven {@code isRole} overlay; an unknown faction counts as an
 * ally (fail closed). GW-DK/AC/MR drop allies and the gunner; TR keeps everyone and flags them. Every non-self target
 * also needs the online gunner's SparkFactionAPI affect permission ({@link PotionGunnerRules#BLAST_ACTION}: Wraith
 * isolation) and Vendetta exact-pair isolation; an offline gunner vetoes nobody, the faction filter still applies.
 * 仅服务端的爆炸目标判定：手雷规则加视线。候选者为生存/冒险模式、存活的对局参与者，其脚下位于 N×N×N 立方体内，
 * 且爆心到其脚部、身体中心或眼睛至少一条 COLLIDER 线段无遮挡（关闭的门会挡住）。阵营取目标真实的 SparkFactionAPI 有效阵营，
 * 绝不使用黑羽鸦 {@code isRole} 伪装覆盖；阵营未知按己方处理（失败关闭）。GW-DK/AC/MR 排除己方与药炮手本人；TR 保留所有人
 * 并加以标记。每个非本人目标还须通过在线药炮手的 SparkFactionAPI 影响许可（冤魂隔离）与复仇者精确配对隔离；
 * 药炮手离线时不做该否决，但阵营过滤照常。
 */
public final class PotionBlastResolver {
    private PotionBlastResolver() {
    }

    /** Targets caught by one blast, in world player order. / 一次爆炸波及的目标，按世界玩家顺序。 */
    public static List<PotionBlastHit> resolve(ServerWorld world, Vec3d center, PotionShellType type,
                                               @Nullable UUID gunnerUuid, @Nullable ServerPlayerEntity gunner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        int size = type.size();
        List<PotionBlastHit> hits = new ArrayList<>();
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (!isCandidate(target)) {
                continue;
            }
            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;
            Box box = target.getBoundingBox();
            if (!PotionBlastRings.inCube(size, dx, dz, box.minY - center.y, box.maxY - center.y)
                    || !hasLineOfSight(world, center, target)) {
                continue;
            }
            boolean self = isSelf(gunnerUuid, target.getUuid());
            boolean ally = isAlly(SparkFactionApi.resolveEffectiveFaction(target, game));
            if (!keeps(type.hitsEveryone(), self, ally, gunner != null,
                    () -> gunnerMayAffect(gunner, target, game))) {
                continue;
            }
            int ring = PotionBlastRings.ring(size, dx, dz);
            hits.add(new PotionBlastHit(target, ring, PotionBlastRings.factor(size, ring), ally, self));
        }
        return List.copyOf(hits);
    }

    /**
     * Pure verdict for a candidate already inside the cube with line of sight. The gunner is kept only by TR; allies
     * only by TR; any other target needs the online gunner's permission (an offline gunner vetoes nobody).
     * 已在立方体内且有视线的候选者的纯判定：药炮手本人只被 TR 保留；己方只被 TR 保留；其余目标需在线药炮手许可
     * （离线时不否决）。
     */
    static boolean keeps(boolean hitsEveryone, boolean self, boolean ally, boolean gunnerOnline,
                         BooleanSupplier gunnerMayAffect) {
        if (self) {
            return hitsEveryone;
        }
        if (ally && !hitsEveryone) {
            return false;
        }
        return !gunnerOnline || gunnerMayAffect.getAsBoolean();
    }

    /** Witch faction, or unknown (fail closed). / 魔女阵营或阵营未知（失败关闭）。 */
    static boolean isAlly(@Nullable Identifier effectiveFaction) {
        return effectiveFaction == null || SparkWitchFactions.WITCH.equals(effectiveFaction);
    }

    static boolean isSelf(@Nullable UUID gunnerUuid, UUID target) {
        return gunnerUuid != null && gunnerUuid.equals(target);
    }

    /** Mirrors the Vendetta packet guard: an active endpoint is reachable only by its exact pair. / 复仇者隔离。 */
    static boolean vendettaAllows(boolean gunnerVendetta, boolean targetVendetta, boolean exactPair) {
        return !(gunnerVendetta || targetVendetta) || exactPair;
    }

    /** Living, playing, survival/adventure; Wathe-dead or role-less players are never caught. / 存活参与者。 */
    private static boolean isCandidate(PlayerEntity player) {
        return GameFunctions.isPlayerAliveAndSurvival(player) && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    private static boolean hasLineOfSight(ServerWorld world, Vec3d center, PlayerEntity target) {
        for (Vec3d point : PotionBlastGeometry.sightPoints(target.getPos(), target.getBoundingBox(),
                target.getEyeY())) {
            if (SeekerDamageRules.segmentClear(world, center, point, null)) {
                return true;
            }
        }
        return false;
    }

    private static boolean gunnerMayAffect(@Nullable ServerPlayerEntity gunner, ServerPlayerEntity target,
                                           GameWorldComponent game) {
        return gunner != null
                && SparkFactionApi.canAffectPlayer(gunner, target, PotionGunnerRules.BLAST_ACTION, game)
                && vendettaAllows(VendettaInteractionService.isActiveVendetta(gunner),
                VendettaInteractionService.isActiveVendetta(target),
                VendettaInteractionService.isExactPair(gunner, target));
    }
}
