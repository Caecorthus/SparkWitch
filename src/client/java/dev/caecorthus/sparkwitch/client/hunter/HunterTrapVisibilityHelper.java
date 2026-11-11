package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.compat.SparkTraitsMoodFlagBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Resolves the client-side visibility decision shared by trap rendering and outlines.
 * 统一决定捕兽夹普通渲染与描边共用的客户端可见性。
 */
public final class HunterTrapVisibilityHelper {
    private HunterTrapVisibilityHelper() {
    }

    public static HunterRules.TrapVisibility visibilityFor(HunterTrapEntity trap, PlayerEntity viewer) {
        return visibilityFor(viewer, () -> viewer.canSee(trap));
    }

    /**
     * Whether this viewer sees the trap through walls (the outline case). Line of sight only separates DIRECT_ONLY
     * from HIDDEN (pinned in HunterRulesTest), so the answer never needs the raycast.
     * 该观察者是否能隔墙看到捕兽夹（描边情形）。视线只区分 DIRECT_ONLY 与 HIDDEN（见 HunterRulesTest），因此无需射线检测。
     */
    public static boolean isVisibleThroughWalls(PlayerEntity viewer) {
        return visibilityFor(viewer, () -> false) == HunterRules.TrapVisibility.THROUGH_WALL;
    }

    private static HunterRules.TrapVisibility visibilityFor(PlayerEntity viewer, BooleanSupplier hasLineOfSight) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return HunterRules.TrapVisibility.HIDDEN;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gameComponent.isRunning()) {
            return HunterRules.TrapVisibility.HIDDEN;
        }

        Role role = gameComponent.getRole(viewer);
        Identifier roleId = role == null ? null : role.identifier();
        boolean killerFaction = role != null && role.getFaction() == Faction.KILLER;
        boolean deadSpectator = isDeadMatchSpectator(viewer, gameComponent);
        boolean trapKiller = HunterRules.isTrapKiller(roleId, killerFaction);
        // Class 1 (police, plus any SparkTraits Impostor holder) and class 2 (every killer, the Loose End and the
        // Undercover) both see a trap through the police rule: line of sight. Class 2 keeps the old instinct x-ray:
        // holding the instinct key turns it into a through-wall view. The trait probe fails closed. Owner 2026-11-11.
        // 第一类（警职，外加任何带 SparkTraits 内鬼词条的玩家）与第二类（所有杀手、亡命徒和卧底）都走警察规则看见捕兽夹：
        // 需要视线。第二类保留旧的透视本能：按住本能键时变为隔墙视野。词条查询失败即关闭（所有者 2026-11-11）。
        boolean policeViewer = HunterRules.isTrapPolice(roleId, isRegisteredPolice(role))
                || trapKiller
                || SparkTraitsMoodFlagBridge.isImpostorActive(viewer);
        // The instinct key stays eager: its Fear gate may post the "instinct blocked" action-bar line, so it keeps its
        // old call count. The pure Obscure/Fear check and the raycast run only when the rule reads them.
        // 本能键保持立即求值：其恐惧门禁可能发送“本能被封锁”动作栏提示，因此调用次数不变。纯粹的障眼/恐惧检查与
        // 射线检测仅在规则需要时执行。
        boolean instinctKey = WatheClient.isInstinctEnabled();
        return HunterRules.trapVisibility(
                roleId,
                deadSpectator,
                trapKiller,
                policeViewer,
                hasLineOfSight,
                () -> instinctKey && !WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()
        );
    }

    private static boolean isRegisteredPolice(@Nullable Role role) {
        if (role == null) {
            return false;
        }
        try {
            return PoliceRoles.contains(role);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean usesAlwaysOnOutline(PlayerEntity viewer) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gameComponent.isRunning()) {
            return false;
        }
        Role role = gameComponent.getRole(viewer);
        return (role != null && role.getFaction() == Faction.KILLER)
                || isDeadMatchSpectator(viewer, gameComponent);
    }

    private static boolean isDeadMatchSpectator(PlayerEntity viewer, GameWorldComponent gameComponent) {
        return gameComponent.isRunning()
                && gameComponent.isPlayerDead(viewer.getUuid())
                && (viewer.isSpectator() || viewer.isCreative());
    }
}
