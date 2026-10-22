package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared owner, ordinary-viewer, and spectator rules for Wraith presentation. Each rule splits into a target part and a
 * viewer-only part; the viewer part is memoized per world pass for the local player ({@link LocalViewerSnapshot}),
 * because these rules run several times per rendered player per frame (highlight HEAD, outline veto, invisibility,
 * held items, name tags). Any other viewer (the integrated server's {@code isInvisibleTo}) is answered fresh.
 * 统一冤魂本人、普通观察者与旁观者的显示规则。每条规则分为目标部分与只与观察者有关的部分；本地玩家的观察者部分按世界 pass
 * 缓存，因为这些规则每帧对每个渲染玩家执行多次（高亮 HEAD、描边否决、隐身、手持物、名牌）。其他观察者（集成服务端的
 * {@code isInvisibleTo}）实时计算。
 */
public final class WraithViewerRules {
    private WraithViewerRules() {
    }

    public static boolean shouldRevealPromotedSaboteurToKiller(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && viewer != target
                && LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.WRAITH_LIVING_KILLER_VIEWER, viewer,
                WraithViewerRules::isLivingKillerViewer)
                && SaboteurRules.isActivePromotedSaboteur(target);
    }

    /** Viewer part of the promoted-Saboteur reveal. / 晋升破坏者显形的观察者部分。 */
    private static boolean isLivingKillerViewer(PlayerEntity viewer) {
        if (viewer.isSpectator()) {
            return false;
        }
        Role viewerRole = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return GameFunctions.isPlayerPlayingAndAlive(viewer)
                && viewerRole != null
                && viewerRole.getFaction() == Faction.KILLER;
    }

    /**
     * The witch faction sees the promoted Curser: body, held items, name tag and its always-on role-colour outline.
     * Every Curser reveal reads this one rule, so they cannot drift apart again.
     * 魔女阵营可见晋升的诅咒者：身体、手持物、名牌以及常驻的职业颜色描边。所有诅咒者显形都读取这一条规则，避免再次不一致。
     */
    public static boolean shouldRevealCurserToWitch(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.WRAITH_WITCH_FACTION_VIEWER, viewer,
                WraithViewerRules::isWitchFactionViewer)
                && CurserFeatureService.isActivePromotedCurser(target);
    }

    /** Viewer part of the promoted-Curser reveal. / 晋升诅咒者显形的观察者部分。 */
    private static boolean isWitchFactionViewer(PlayerEntity viewer) {
        return WitchFactionRules.isWitchFactionMember(GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer));
    }

    public static boolean shouldHideFromOrdinaryViewer(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && WraithClientState.isActive(target)
                && !viewer.getUuid().equals(target.getUuid())
                && !isDeadSpectator(viewer)
                && !shouldRevealPromotedSaboteurToKiller(viewer, target)
                && !shouldRevealCurserToWitch(viewer, target)
                && !VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target);
    }

    public static boolean shouldRevealToSpectator(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && isDeadSpectator(viewer)
                && WraithClientState.isActive(target);
    }

    /**
     * Only a Wathe-dead spectator bypasses Wraith privacy. A living spectator (a Rift Gate occupant, a SparkTraits
     * Depression fake death) is an ordinary viewer, so holding instinct never shows it a Wraith's real role colour.
     * 只有已死亡（wathe 判定）的旁观者可越过冤魂隐私。存活的旁观者（裂隙门内玩家、SparkTraits 抑郁假死）属于普通
     * 观察者，按住本能键也看不到冤魂的真实职业颜色。
     */
    private static boolean isDeadSpectator(PlayerEntity viewer) {
        return LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.WRAITH_DEAD_SPECTATOR, viewer,
                WraithViewerRules::evaluateDeadSpectator);
    }

    private static boolean evaluateDeadSpectator(PlayerEntity viewer) {
        return viewer.isSpectator() && !GameFunctions.isPlayerPlayingAndAlive(viewer);
    }
}
