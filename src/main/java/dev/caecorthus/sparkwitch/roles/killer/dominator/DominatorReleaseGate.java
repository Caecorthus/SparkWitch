package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightDuelSkillLock;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaTargetingPacketGuard;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowBodyGuards;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.util.GunShootPayload;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Target-side release gate (owner decision: a player whose own {@code wathe:gunshoot} would be refused cannot be
 * forced to shoot, and the refusal costs nothing). The forced shot calls Wathe's gun receiver directly, so it skips
 * every packet-level guard on {@code wathe:gunshoot}; this asks the same rules about the marked player instead, each
 * guard reusing its own payload list and state predicate so a list change carries over here. Server only. Fail
 * closed: a check that throws counts as blocked.
 * 目标侧释放门槛（所有者决定：自己的 {@code wathe:gunshoot} 会被拒绝的玩家不能被强制开枪，且拒绝不消耗任何东西）。强制开枪直接
 * 调用 Wathe 的枪械接收器，因此绕过了 {@code wathe:gunshoot} 上所有数据包层面的拦截；这里改为就被标记者向同样的规则询问，
 * 每项拦截都复用其自身的数据包名单与状态谓词，名单变化会自动带到这里。仅服务端。失败即关闭：抛出异常的检查视为拦截。
 *
 * <p>Not reachable from SparkWitch (no existing bridge or public API at the pinned versions), reported as cross-repo
 * follow-ups: SparkStrength's Taotie head daze ({@code TaotieHeadRules.DAZE_BLOCKED_PAYLOADS}) and SparkFactionAPI's
 * anti-fast-round safe time ({@code AntiFastRoundRules.BLOCKED_PAYLOADS}). Fear never blocks {@code wathe:gunshoot}.
 * SparkWitch 无法触及（固定版本下没有现成桥接或公共 API），作为跨仓库后续事项报告：SparkStrength 饕餮头颅眩晕与
 * SparkFactionAPI 开局安全时间。恐惧从不拦截 {@code wathe:gunshoot}。</p>
 */
public final class DominatorReleaseGate {
    /** The payload every guard below is asked about. / 下列每项拦截被询问的数据包。 */
    static final Identifier GUNSHOOT_ID = GunShootPayload.ID.id();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private DominatorReleaseGate() {
    }

    /**
     * Whether forcing {@code marked} to shoot now must be refused. Cheapest checks first; each one is fail-closed.
     * 此刻是否必须拒绝强制 {@code marked} 开枪。先做开销最小的检查；每项都失败即关闭。
     */
    public static boolean isForcedShotBlocked(ServerPlayerEntity marked) {
        return marked == null
                // Wathe's receiver itself returns for spectators; swallowed players and Rift occupants are living
                // spectators. / Wathe 接收器自身对旁观者直接返回；被吞下的玩家与裂隙门内的玩家都是活体旁观者。
                || blocked(marked::isSpectator)
                || blocked(() -> NoellesTaotieSeekerBridge.isSwallowed(marked))
                // Riftwalker occupant guard (RiftSessionPayloadGuardMixin). / 隙行者门内拦截。
                || blocked(() -> RiftSessionRules.isBlockedWhileInside(GUNSHOOT_ID)
                        && RiftSessionService.isInside(marked))
                // Control Expert stun (ControlExpertStunPayloadGuardMixin). / 控场专家眩晕。
                || blocked(() -> ControlExpertStunRules.isBlockedPayload(GUNSHOOT_ID)
                        && ControlExpertStun.isStunned(marked))
                // Seeker remote view (SeekerSessionPayloadGuardMixin). / 搜寻者遥控视角。
                || blocked(() -> SeekerRemoteRules.isBlockedWhileViewing(GUNSHOOT_ID)
                        && SeekerRemoteSessionService.isLocked(marked))
                // Black Raven crow body (BlackRavenCrowBodyPayloadGuardMixin). / 黑羽鸦化鸦本体。
                || blocked(() -> BlackRavenCrowBodyGuards.blocksPayloadInCrowForm(GUNSHOOT_ID)
                        && BlackRavenCrowForm.isInCrowForm(marked))
                // SparkTraits Last Escape cancels the receiver at HEAD (LastEscapeGunReceiverMixin), which would
                // otherwise spend the use on a shot that never fires. / SparkTraits 脱险会在接收器 HEAD 处取消射击，
                // 否则次数会白白耗在一发不会射出的枪上。
                || blocked(() -> SparkTraitsKillerBridge.isLastEscapeActive(marked))
                // Knight duel: both duelists' revolvers are sealed (KnightDuelSeal), and the forced shot always fires a
                // revolver. / 骑士决斗：双方的左轮都被封印，而强制开枪始终使用左轮。
                || blocked(() -> KnightDuelSkillLock.isLocked(marked))
                // Kidnapper knockout control: the victim's attack and use keys are locked on its client, so it can
                // never fire. / 绑架者迷药控制：受害者客户端的攻击与使用键被锁定，因此永远无法开枪。
                || blocked(() -> isKidnapped(marked))
                // SparkStrength Partygoer carry (PartygoerCarriedPayloadGuardMixin drops the daze list, which holds
                // wathe:gunshoot); a pure entity-type check through the existing bridge.
                // SparkStrength 派对客扛人（其拦截丢弃包含 wathe:gunshoot 的眩晕名单）；经现有桥接做纯实体类型判断。
                || blocked(() -> SparkStrengthPartygoerCompat.isPartygoer(marked.getVehicle()))
                // Remote viewing through another camera (SparkStrength drone, reporter feed, ...). / 借用其他镜头远程观看。
                || blocked(() -> marked.getCameraEntity() != marked)
                // Vendetta pair isolation (ServerPlayNetworkAddonFearMixin), judged on the player the shot would hit.
                // 仇杀配对隔离，按这一枪将会命中的玩家判定。
                || blocked(() -> VendettaTargetingPacketGuard.shouldBlock(marked,
                        new GunShootPayload(forcedShotTargetId(marked))));
    }

    /**
     * The entity id the forced shot would hit now, from the one shared pick {@link DominatorForcedShotAim#resolve}
     * that {@code DominatorForcedShotService.fireNow} fires on; -1 when nothing is hit.
     * 强制开枪此刻将会命中的实体 id，来自 {@code DominatorForcedShotService.fireNow} 开枪所用的同一选靶
     * {@link DominatorForcedShotAim#resolve}；未命中为 -1。
     */
    static int forcedShotTargetId(ServerPlayerEntity shooter) {
        return DominatorForcedShotAim.resolve(shooter).targetId();
    }

    private static boolean isKidnapped(ServerPlayerEntity player) {
        KidnapperControlComponent control = KidnapperControlComponent.KEY.getNullable(player);
        return control != null && control.isControlled();
    }

    private static boolean blocked(BooleanSupplier check) {
        try {
            return check.getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            // A broken optional seam must never let a guarded player be forced to shoot. / 损坏的可选接缝绝不能让受保护的玩家被强制开枪。
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                SparkWitch.LOGGER.warn("Dominator release check failed; refusing the forced shot", failure);
            }
            return true;
        }
    }
}
