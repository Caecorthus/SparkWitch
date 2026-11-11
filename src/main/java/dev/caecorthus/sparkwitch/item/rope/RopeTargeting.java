package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTaserTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowLandingHold;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotComponent;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeHypnosisGuards;
import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignService;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerStatusProbes;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only gates of the Rope: who may strangle, who may be strangled and the Taser-style target pick. The Rope has
 * no role gate (anyone holding it may use it), so every predicate reads public round and control state only.
 * 绳索的服务端判定：谁可以勒杀、谁可以被勒杀，以及电击枪式的目标选取。绳索没有职业门槛（任何持有者都可使用），因此所有判定只读取公开的
 * 对局与受控状态。
 */
final class RopeTargeting {
    private RopeTargeting() {
    }

    /**
     * Living, non-spectator, non-creative, non-Wraith role holder of a running game
     * ({@link RoundParticipants#ROLE_HOLDER}) who is still connected.
     * 进行中对局里存活、非旁观、非创造、非冤魂且拥有职业的在线玩家（{@link RoundParticipants#ROLE_HOLDER}）。
     */
    static boolean isParticipant(@Nullable ServerPlayerEntity player) {
        return player != null && !player.isDisconnected() && !player.isRemoved()
                && RoundParticipants.ROLE_HOLDER.test(player);
    }

    /**
     * The start gate: an ACTIVE round, a participant who is in no strangle and under nobody's control.
     * 开始门槛：回合处于 ACTIVE，使用者是参与者、不在任何勒杀中且未受任何控制。
     */
    static boolean mayAct(ServerPlayerEntity user) {
        return GameWorldComponent.KEY.get(user.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && isParticipant(user)
                && !RopeStrangleHold.isHeld(user)
                && !isControlled(user);
    }

    /**
     * Whether something other than the strangle itself holds, hides or controls {@code user}; checked at the start and
     * again every tick of a running strangle, where it is the "stunned / feared / hypnotised / controlled" interrupt.
     * It reads the Taser stun and the Raven landing hold directly rather than {@code ControlExpertStun.isStunned},
     * which also answers true for the Rope's own hold. SparkStrength: its {@code sparkstrength:engineer_stunned} lock
     * (the Engineer's capture device and the Jester Moment's freeze share that component) is read through the existing
     * optional probe {@code RiftwalkerStatusProbes.isCaptureStunned}, which fails open; its Taotie head daze has no
     * SparkWitch seam and is not covered.
     * 是否有勒杀之外的机制持有、隐藏或控制着 {@code user}；开始时检查一次，勒杀进行中的每一刻再次检查，即「被眩晕 / 恐惧 / 催眠 / 控制」
     * 的打断条件。这里直接读取电击眩晕与黑羽鸦落地定身，而不读 {@code ControlExpertStun.isStunned}，后者对绳索自身的定身同样返回
     * true。SparkStrength：其 {@code sparkstrength:engineer_stunned} 锁定（工程师捕捉装置与小丑时刻的全员定身共用该组件）通过已有的
     * 可选探针 {@code RiftwalkerStatusProbes.isCaptureStunned} 读取（失败时放行）；饕餮头颅眩晕在 SparkWitch 中没有接缝，未覆盖。
     */
    static boolean isControlled(ServerPlayerEntity user) {
        ControlExpertStatusComponent stun = ControlExpertStatusComponent.KEY.getNullable(user);
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(user);
        return stun != null && stun.isStunned()
                || BlackRavenCrowLandingHold.isHeld(user)
                || GrandWitchFearService.isPlayerFeared(user)
                || NatsumeHypnosisGuards.isLocked(user)
                || kidnap != null && kidnap.isControlled()
                || DominatorForcedShotComponent.isSequenceActive(user)
                || RiftwalkerStatusProbes.isCaptureStunned(user)
                || NoellesTaotieSeekerBridge.isSwallowed(user)
                || SparkTraitsSeekerBridge.isLastStandPending(user)
                || SparkTraitsKillerBridge.isLastEscapeActive(user)
                || SeekerRemoteSessionService.isLocked(user)
                || SeekerInventoryRules.isInSession(user)
                || user.getCameraEntity() != user
                || isOutOfBody(user)
                || isDuelist(user);
    }

    /**
     * The target gate, everything but the aim and the back check: another participant in the user's world whom
     * SparkFactionAPI lets the user affect (action id = the death reason, the id its kill veto will ask for again),
     * not hidden or untouchable (Last Escape, Last Stand, swallowed, feigning, a Cat spectre, inside a Rift, in Raven
     * Form, carried by the Kidnapper) and in no strangle on either side.
     * 目标门槛（不含瞄准与背后判定）：与使用者同一世界的另一名参与者，SparkFactionAPI 允许使用者影响它（动作 id 即死因，其击杀否决届时
     * 会再次询问同一 id），未被隐藏或不可触碰（脱险、背水一战、被吞下、假死、猫的幽魂阶段、裂隙内、化鸦中、被绑匪搬运），且不在任何
     * 勒杀的任一方。
     */
    static boolean mayTarget(ServerPlayerEntity user, ServerPlayerEntity target) {
        if (target == user || target.getUuid().equals(user.getUuid()) || target.getWorld() != user.getWorld()
                || !isParticipant(target)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        return SparkFactionApi.canAffectPlayer(user, target, SparkWitchDeathReasons.STRANGLED, game)
                && staysVictim(target)
                && !RopeStrangleHold.isHeld(target);
    }

    /**
     * What a strangled player must stay to remain in the grip; losing it mid-channel releases them.
     * 被勒者要保持被控制所必须满足的状态；引导中途不再满足即被放开。
     */
    static boolean staysVictim(ServerPlayerEntity target) {
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(target);
        return isParticipant(target)
                && !SparkTraitsKillerBridge.isLastEscapeActive(target)
                && !SparkTraitsSeekerBridge.isLastStandPending(target)
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !(kidnap != null && kidnap.isControlled())
                && !isOutOfBody(target);
    }

    /**
     * Nearest eligible player on the user's eye ray, cut at the first block (so line of sight is required), against
     * lag-compensated hit volumes: the Taser's geometry with the Rope's reach. A strangle then holds both players
     * where they stand NOW, so the pick must also hold on current server state ({@link #isInReachNow}); a volume left
     * behind by a sprint or a teleport picks nobody.
     * 使用者视线射线上最近的合格玩家，射线在第一个方块处截断（因此要求视线无遮挡），按延迟补偿后的命中体积判定：沿用电击枪的几何，
     * 距离取绳索的距离。勒杀随后会把双方定在「此刻」所站的位置，因此选中的目标还必须在当前服务端状态下成立
     * （{@link #isInReachNow}）；冲刺或传送后残留的体积选不中任何人。
     */
    static @Nullable ServerPlayerEntity findTarget(ServerPlayerEntity user) {
        ServerPlayerEntity picked = ControlExpertTaserTargeting.findTarget(user, RopeRules.RANGE,
                user.getServerWorld().getPlayers(),
                candidate -> mayTarget(user, candidate),
                candidate -> PlayerHitboxHistory.hitVolumes(user, candidate, RopeRules.TARGET_BOX_EXPANSION));
        return picked != null && isInReachNow(user, picked) ? picked : null;
    }

    /**
     * Current server positions only: the target's body is within the Rope's reach of the user's eye and vanilla line
     * of sight (eye to eye, collider blocks) is clear.
     * 仅看当前服务端位置：目标身体在使用者眼睛的绳索距离内，且原版视线（眼对眼、碰撞方块）无遮挡。
     */
    static boolean isInReachNow(ServerPlayerEntity user, ServerPlayerEntity target) {
        return RopeRules.confirmsGrab(eyeToBodySquared(user, target), user.canSee(target));
    }

    /** The reach metric on current positions: user's eye to the target's body box. / 当前位置下的距离度量：使用者眼睛到目标身体箱体。 */
    static double eyeToBodySquared(ServerPlayerEntity user, ServerPlayerEntity target) {
        Box body = target.getBoundingBox();
        return RopeRules.distanceSquaredToBox(user.getX(), user.getEyeY(), user.getZ(),
                body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ);
    }

    /** The back check on current server positions and the target's head yaw. / 按当前服务端位置与目标头部朝向做背后判定。 */
    static boolean isBehind(ServerPlayerEntity user, ServerPlayerEntity target) {
        return RopeRules.isBehind(target.getX(), target.getZ(), target.getHeadYaw(), user.getX(), user.getZ());
    }

    static boolean holdsRope(PlayerEntity player) {
        return player.getMainHandStack().isOf(SparkWitchItems.rope())
                || player.getOffHandStack().isOf(SparkWitchItems.rope());
    }

    // Not in their own visible body: a feign (Fiend, Charon, Hiro's victims), a Cat spectre, a Rift, Raven Form, or an
    // active Vendetta. / 不在自己可见的身体里：假死（魔人、卡戎、希罗的受害者）、猫的幽魂阶段、裂隙、化鸦，或激活的仇杀客。
    private static boolean isOutOfBody(ServerPlayerEntity player) {
        return FiendFeignService.isFeigning(player)
                || FerrymanFeignService.isFeigning(player)
                || HiroReturnService.isFeignedVictim(player)
                || SchrodingersCatService.isSpectre(player)
                || RiftSessionService.isInside(player)
                || BlackRavenCrowForm.isInCrowForm(player)
                || VendettaInteractionService.isActiveVendetta(player);
    }

    // A running Knight duel seals every weapon but the knife and the derringer for its pair. / 进行中的骑士决斗对决斗双方封印刀与德林加之外的所有武器。
    private static boolean isDuelist(ServerPlayerEntity player) {
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.getNullable(player.getWorld());
        return duel != null && duel.active() && duel.isDuelist(player.getUuid());
    }
}
