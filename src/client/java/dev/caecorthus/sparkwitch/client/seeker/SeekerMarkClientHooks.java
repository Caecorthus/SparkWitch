package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client seam: the owner's breaker-mark outline (plan §3.14). The mark target and countdown live only in the
 * owner-synced {@code sparkwitch:seeker_status}, so only the Seeker can ever see it; the server alone decides who is
 * marked. {@code always(MARK_COLOR, 95)} sits above SparkWitch's 90 outlines and below {@code skip()} (100), Black
 * Raven (101) and suppression (102). It is hidden while the target is a spectator or swallowed, while SparkTraits
 * hides the target, or while the viewer is swallowed. Nothing here names the target.
 * 客户端接缝：拥有者的损坏者标记描边（计划 §3.14）。标记目标与倒计时只存在于仅同步给拥有者的
 * {@code sparkwitch:seeker_status} 中，因此只有搜寻者本人能看到；由谁被标记完全由服务端决定。
 * {@code always(MARK_COLOR, 95)} 高于 SparkWitch 的 90 级描边，低于 {@code skip()}（100）、黑鸦（101）与压制（102）。
 * 目标为旁观者或被吞噬、SparkTraits 隐藏目标，或观察者自身被吞噬时不显示。这里从不显示目标名字。
 */
public final class SeekerMarkClientHooks {
    private static boolean registered;

    private SeekerMarkClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SeekerMarkClientHooks::sparkwitch$markHighlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult sparkwitch$markHighlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer) || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        UUID markTarget = SeekerClientState.markTarget();
        if (markTarget == null || !markTarget.equals(targetPlayer.getUuid())) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == targetPlayer || !SeekerClientState.isSeeker()) {
            return null;
        }
        boolean shows = SeekerInstinctRules.showsMark(markTarget, SeekerClientState.markRemainingTicks(),
                targetPlayer.getUuid(),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                NoellesTaotieSeekerBridge.isSwallowed(targetPlayer),
                SeekerInstinctVisibilityBridge.isHidden(viewer, targetPlayer),
                NoellesTaotieSeekerBridge.isSwallowed(viewer));
        return shows
                ? GetInstinctHighlight.HighlightResult.always(SeekerRules.MARK_COLOR, SeekerRules.MARK_OUTLINE_PRIORITY)
                : null;
    }
}
