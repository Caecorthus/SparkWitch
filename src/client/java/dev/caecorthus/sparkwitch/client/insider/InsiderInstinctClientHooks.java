package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.corruptcop.CorruptCopPlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam: the Insider's single Wathe {@link GetInstinctHighlight} listener (the Insider's own view at
 * {@link InsiderHighlightRules#INSIDER_VIEW_PRIORITY}, the Corrupt Cop view at {@link InsiderHighlightRules#PRIORITY}).
 * Roles and the Corrupt Cop Moment state are read from components NoellesRoles and Wathe already sync to every client;
 * nothing new is sent. Existing hide rules keep working: HEAD vetoes (Wraith privacy, fear, Taotie, SparkTraits)
 * answer before the event, {@code skip()} (100) and suppression (102) outrank this listener, invisible targets are
 * left alone, and a SparkTraits {@code isInstinctHidden} target gets no Insider outline. Killers get no answer, so
 * Wathe's default paints the Insider like any other passenger.
 * 客户端接缝：内应唯一的 Wathe {@link GetInstinctHighlight} 监听器（内应自身视角优先级
 * {@link InsiderHighlightRules#INSIDER_VIEW_PRIORITY}，黑警视角优先级 {@link InsiderHighlightRules#PRIORITY}）。
 * 职业与黑警时刻状态读取自 NoellesRoles 和 Wathe 已同步给所有客户端的组件，不发送任何新数据。现有隐藏规则继续生效：
 * HEAD 否决（冤魂隐私、恐惧、饕餮、SparkTraits）先于事件作答，{@code skip()}（100）与压制（102）高于本监听器，
 * 不处理隐身目标，SparkTraits {@code isInstinctHidden} 隐藏的目标不会得到内应描边。杀手不会得到答复，
 * 因此 Wathe 默认逻辑会把内应与其他乘客一样描绘。
 */
public final class InsiderInstinctClientHooks {
    private InsiderInstinctClientHooks() {
    }

    /** Wathe listener; {@code null} means "no opinion". / Wathe 监听器；{@code null} 表示不作答。 */
    public static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity playerTarget) || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role viewerRole = game.getRole(viewer);
        Role targetRole = game.getRole(playerTarget);
        if (!InsiderHighlightRules.mayAnswer(viewerRole, targetRole)) {
            return null;
        }
        InsiderHighlightRules.Viewer kind = InsiderHighlightRules.viewer(
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer)
        );
        if (!isEligibleTarget(kind, viewer, playerTarget)) {
            return null;
        }
        boolean instinctKey = WatheClient.isInstinctEnabled();
        boolean visionWindow = kind == InsiderHighlightRules.Viewer.CORRUPT_COP
                && !instinctKey
                && isCorruptCopVisionWindow(viewer);
        Integer color = InsiderHighlightRules.color(kind, instinctKey, visionWindow, targetRole);
        if (color == null || InsiderSparkTraitsBridge.isInstinctHidden(viewer, playerTarget)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(color, InsiderHighlightRules.priority(kind, targetRole));
    }

    private static boolean isEligibleTarget(InsiderHighlightRules.Viewer kind, PlayerEntity viewer, PlayerEntity target) {
        return InsiderHighlightRules.isEligibleTarget(
                kind,
                viewer.getUuid().equals(target.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(target),
                GameFunctions.isPlayerSpectatingOrCreative(target),
                target.isInvisible()
        );
    }

    /**
     * NoellesRoles' synced Corrupt Cop Moment vision phase (the 10 s always-on half of its 30 s cycle). The component is
     * attached to every player; a missing one reads as "no window".
     * NoellesRoles 同步的黑警时刻透视阶段（30 秒循环中常亮的 10 秒）。该组件挂在所有玩家身上；缺失时视为不在窗口内。
     */
    private static boolean isCorruptCopVisionWindow(PlayerEntity viewer) {
        return CorruptCopPlayerComponent.KEY.maybeGet(viewer)
                .map(CorruptCopPlayerComponent::canSeePlayersThroughWalls)
                .orElse(false);
    }
}
