package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
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
 * Client seam: the Insider's single Wathe {@link GetInstinctHighlight} listener at
 * {@link InsiderHighlightRules#PRIORITY}, plus the Impostor-viewer recolor called by
 * {@code WatheClientInsiderImpostorHighlightMixin}. Roles and the Corrupt Cop Moment state are read from components
 * NoellesRoles and Wathe already sync to every client; nothing new is sent. Existing hide rules keep working:
 * HEAD vetoes (Wraith privacy, fear, Taotie, SparkTraits) answer before the event, {@code skip()} (100) and
 * suppression (102) outrank this listener, invisible targets are left alone, and a SparkTraits
 * {@code isInstinctHidden} target gets no Insider outline.
 * 客户端接缝：内应唯一的 Wathe {@link GetInstinctHighlight} 监听器（优先级 {@link InsiderHighlightRules#PRIORITY}），
 * 以及供 {@code WatheClientInsiderImpostorHighlightMixin} 调用的内鬼观察者换色。职业与黑警时刻状态读取自
 * NoellesRoles 和 Wathe 已同步给所有客户端的组件，不发送任何新数据。现有隐藏规则继续生效：HEAD 否决（冤魂隐私、恐惧、
 * 饕餮、SparkTraits）先于事件作答，{@code skip()}（100）与压制（102）高于本监听器，隐身目标不处理，
 * SparkTraits {@code isInstinctHidden} 隐藏的目标不会得到内应描边。
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
        InsiderHighlightRules.Viewer kind = InsiderHighlightRules.viewer(
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                WatheClient.isKiller()
        );
        if (kind == InsiderHighlightRules.Viewer.NONE || !isEligibleTarget(viewer, playerTarget)) {
            return null;
        }
        boolean instinctKey = WatheClient.isInstinctEnabled();
        boolean visionWindow = kind == InsiderHighlightRules.Viewer.CORRUPT_COP
                && !instinctKey
                && isCorruptCopVisionWindow(viewer);
        Integer color = InsiderHighlightRules.color(kind, instinctKey, visionWindow, game.getRole(playerTarget));
        if (color == null || InsiderSparkTraitsBridge.isInstinctHidden(viewer, playerTarget)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(color, InsiderHighlightRules.PRIORITY);
    }

    /**
     * Final answer rewrite for the SparkTraits Impostor viewer (D3). Called with the finished Wathe result; the fast path
     * compares one int, so ordinary frames cost nothing.
     * SparkTraits 内鬼观察者的最终结果改写（D3）。以 Wathe 的最终结果调用；快速路径只比较一个整数，普通帧没有额外开销。
     */
    public static int recolorImpostorView(Entity target, int highlight) {
        if (highlight != InsiderHighlightRules.SPARKTRAITS_CIVILIAN_INSTINCT_COLOR
                || !(target instanceof PlayerEntity playerTarget)
                || !SparkWitchServerConnection.isConfirmedServer()) {
            return highlight;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !InsiderParticipation.isInsider(playerTarget) || !isEligibleTarget(viewer, playerTarget)) {
            return highlight;
        }
        boolean livingImpostor = GameFunctions.isPlayerPlayingAndAlive(viewer)
                && !GameFunctions.isPlayerSpectatingOrCreative(viewer)
                && InsiderSparkTraitsBridge.hasActiveTrait(viewer, InsiderSparkTraitsBridge.IMPOSTOR);
        return InsiderHighlightRules.recolorImpostorView(highlight, livingImpostor, true);
    }

    private static boolean isEligibleTarget(PlayerEntity viewer, PlayerEntity target) {
        return InsiderHighlightRules.isEligibleTarget(
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
