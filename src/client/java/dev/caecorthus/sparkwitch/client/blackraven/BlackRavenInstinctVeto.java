package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Plan §3.2 (owner 2026-10-21): a living player whose RAW Wathe role is the Black Raven is never highlighted by any
 * instinct source or outline, for every viewer except itself and viewers who may see spectator information (Wathe's
 * {@code canSeeSpectatorInformation}: spectator or creative AND not a living participant, i.e. dead spectators,
 * spectating non-participants, creative admins), who see everything as before. Living participants in spectator mode
 * (a Rift Gate occupant, a feign-dead Fiend) keep their living instinct and are vetoed like anyone else (plan §2.2: only
 * DEAD spectators are exempt). Wathe syncs the full role map to every client, so {@code getRole} is the raw role here;
 * the disguise acting overlay only widens {@code isRole}. Fail closed: an unconfirmed server, a missing viewer or an
 * unknown role never vetoes. Applied by the outermost {@code BlackRavenInstinctVetoMixin} ({@code getInstinctHighlight}
 * → -1) and {@code BlackRavenOutlineVetoMixin} ({@code hasOutline} → false).
 * 计划 §3.2（所有者 2026-10-21）：原始 Wathe 职业为黑羽鸦的存活玩家，对任何观察者都不会被任何本能来源高亮或描边；例外是其
 * 本人，以及可查看旁观信息的观察者（Wathe 的 {@code canSeeSpectatorInformation}：旁观或创造模式，且不是存活的参与者，即死亡
 * 旁观者、旁观中的非参与者、创造模式管理员），他们照旧看到一切。处于旁观模式的存活参与者（传送门内的玩家、假死的魔人）仍保有存活
 * 时的本能，与其他人一样被否决（计划 §2.2：只有死亡旁观者豁免）。Wathe 会把完整职业表同步到每个客户端，因此此处
 * {@code getRole} 即原始职业；伪装扮演覆盖层只放宽 {@code isRole}。失败即关闭：服务端未确认、无观察者或职业未知时从不否决。
 * 由最外层的 {@code BlackRavenInstinctVetoMixin}（{@code getInstinctHighlight} 返回 -1）与
 * {@code BlackRavenOutlineVetoMixin}（{@code hasOutline} 返回 false）执行。
 */
public final class BlackRavenInstinctVeto {
    private BlackRavenInstinctVeto() {
    }

    /** Whether {@code viewer} must not see any instinct highlight or outline on {@code target}. / 观察者是否不得看到目标的任何本能高亮或描边。 */
    public static boolean hides(@Nullable PlayerEntity viewer, @Nullable Entity target) {
        if (viewer == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        // Raven Form (plan §3.2, §2.5): neither the left-behind body nor the crow is ever outlined, for every viewer
        // except spectator-information ones. 化鸦（计划 §3.2、§2.5）：被留下的本体与乌鸦从不被描边，可查看旁观信息的观察者除外。
        if (BlackRavenCrowForm.isBodyOrCrow(target)) {
            return vetoesRavenForm(seesSpectatorInformation(viewer));
        }
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return false;
        }
        Role targetRole = GameWorldComponent.KEY.get(targetPlayer.getWorld()).getRole(targetPlayer);
        if (!BlackRavenRules.isBlackRaven(targetRole)) {
            return false;
        }
        return vetoes(
                viewer == targetPlayer,
                seesSpectatorInformation(viewer),
                true,
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
        );
    }

    /**
     * The exemption, per viewer (Wathe's {@code WatheClient.canSeeSpectatorInformation} for any viewer, not only the
     * local one); shared with {@code BlackRavenCrowBodyVisibility}. Viewer-only, so the local player's answer is
     * memoized per world pass ({@link LocalViewerSnapshot}). / 按观察者计算的豁免（对任意观察者等价于 Wathe 的
     * canSeeSpectatorInformation，而不仅是本地玩家）；与 BlackRavenCrowBodyVisibility 共用。只与观察者有关，因此本地玩家的
     * 结果按世界 pass 缓存。
     */
    public static boolean seesSpectatorInformation(@Nullable PlayerEntity viewer) {
        return LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.BLACK_RAVEN_SEES_SPECTATOR_INFORMATION, viewer,
                BlackRavenInstinctVeto::evaluateSeesSpectatorInformation);
    }

    private static boolean evaluateSeesSpectatorInformation(@Nullable PlayerEntity viewer) {
        return viewer != null && seesSpectatorInformation(GameFunctions.isPlayerSpectatingOrCreative(viewer),
                GameFunctions.isPlayerPlayingAndAlive(viewer));
    }

    /** Pure exemption: spectator or creative, and not a living participant. / 纯豁免判定：旁观或创造模式，且不是存活参与者。 */
    public static boolean seesSpectatorInformation(boolean viewerSpectatingOrCreative, boolean viewerPlayingAndAlive) {
        return viewerSpectatingOrCreative && !viewerPlayingAndAlive;
    }

    /** Pure veto decision. / 纯否决判定。 */
    static boolean vetoes(boolean self, boolean viewerSeesSpectatorInformation, boolean targetRawBlackRaven,
                          boolean targetPlayingAndAlive) {
        return !self && !viewerSeesSpectatorInformation && targetRawBlackRaven && targetPlayingAndAlive;
    }

    /** Pure veto for a Raven Form body or crow. / 化鸦本体或乌鸦的纯否决判定。 */
    static boolean vetoesRavenForm(boolean viewerSeesSpectatorInformation) {
        return !viewerSeesSpectatorInformation;
    }
}
