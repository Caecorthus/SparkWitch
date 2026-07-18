package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMarkPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Resolves Feather marks and the terminal sensed-only player outline policy. / 裁决羽刃标记与感知模式的最终玩家外框规则。 */
public final class BlackRavenInstinctClientHooks {
    private static final int FEATHER_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 1;
    private static boolean registered;

    private BlackRavenInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(BlackRavenInstinctClientHooks::featherHighlight);
    }

    public static int resolveHighlight(int originalColor, Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer != null && target instanceof PlayerEntity targetPlayer) {
            Integer vendettaHighlight = VendettaClientPresentation.highlight(viewer, targetPlayer);
            if (vendettaHighlight != null) {
                return vendettaHighlight;
            }
        }
        if (viewer == null || !BlackRavenClientState.isEligible(viewer)) {
            return originalColor;
        }

        boolean marked = isMarkedForLocalRaven(target);
        if (BlackRavenClientState.isPerceptionActive(viewer)) {
            return marked ? BlackRavenRules.COLOR : -1;
        }
        if (BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.NORMAL) {
            return marked ? BlackRavenRules.COLOR : originalColor;
        }
        if (!(target instanceof PlayerEntity targetPlayer)
                || !WatheClient.isInstinctEnabled()
                || originalColor < 0
                || !isPubliclyVisible(viewer, targetPlayer)) {
            return -1;
        }

        BlackRavenIdentitySnapshot snapshot = BlackRavenClientState.snapshot(viewer, targetPlayer);
        return snapshot == null ? -1 : snapshot.roleColor();
    }

    public static boolean isPubliclyVisible(PlayerEntity viewer, PlayerEntity target) {
        return target != viewer
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisibleTo(viewer)
                && !SparkTraitsInstinctVisibilityBridge.isHidden(viewer, target);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult featherHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !BlackRavenClientState.isEligible(viewer)
                || BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.SENSED_ONLY
                || !isMarkedForLocalRaven(target)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(BlackRavenRules.COLOR, FEATHER_PRIORITY);
    }

    private static boolean isMarkedForLocalRaven(Entity target) {
        return target instanceof PlayerEntity targetPlayer
                && BlackRavenMarkPlayerComponent.KEY.get(targetPlayer).isMarkedForLocalRaven();
    }
}
