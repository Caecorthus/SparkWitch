package dev.caecorthus.sparkwitch.client.vendetta;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPresentationRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaRole;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Resolves recipient-safe Vendetta presentation from synchronized component state. / 仅用收件人安全的同步状态裁决仇杀客显示。 */
public final class VendettaClientPresentation {
    private VendettaClientPresentation() {
    }

    public static boolean isActiveVendetta(@Nullable PlayerEntity player) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || player == null
                || !WraithClientState.isActive(player)) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && VendettaRole.ROLE_ID.equals(role.identifier());
    }

    public static boolean hasActiveOwnerState(@Nullable PlayerEntity player) {
        return isActiveVendetta(player) && VendettaPlayerComponent.KEY.get(player).isActive();
    }

    public static @Nullable PlayerEntity boundKiller(PlayerEntity vendetta) {
        if (!hasActiveOwnerState(vendetta)) {
            return null;
        }
        UUID killerUuid = VendettaPlayerComponent.KEY.get(vendetta).getBoundKillerUuid();
        return killerUuid == null ? null : vendetta.getWorld().getPlayerByUuid(killerUuid);
    }

    public static float desaturation(PlayerEntity viewer) {
        PlayerEntity killer = boundKiller(viewer);
        double distance = killer == null
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(viewer.squaredDistanceTo(killer));
        return VendettaPresentationRules.desaturation(distance);
    }

    public static @Nullable Integer highlight(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || viewer == target) {
            return null;
        }
        if (isBoundKillerViewingVendetta(viewer, target)) {
            return VendettaPresentationRules.VENDETTA_HIGHLIGHT_COLOR;
        }
        if (!hasActiveOwnerState(viewer)) {
            return null;
        }
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(viewer);
        UUID killerUuid = component.getBoundKillerUuid();
        if (killerUuid == null || !killerUuid.equals(target.getUuid())) {
            return null;
        }
        double distance = Math.sqrt(viewer.squaredDistanceTo(target));
        return VendettaPresentationRules.shouldHighlightKiller(distance, component.getRevealActiveTicks())
                ? VendettaPresentationRules.KILLER_HIGHLIGHT_COLOR
                : null;
    }

    /**
     * The marker is deliberately recipient-specific: it cannot expose the killer UUID to other clients.
     * 此标记严格按收件人同步，不能向其他客户端泄露凶手 UUID。
     */
    public static boolean isBoundKillerViewingVendetta(
            @Nullable PlayerEntity viewer,
            @Nullable PlayerEntity target
    ) {
        return viewer != null
                && target != null
                && viewer != target
                && viewer == MinecraftClient.getInstance().player
                && isActiveVendetta(target)
                && VendettaPlayerComponent.KEY.get(target).isBoundViewer();
    }

    public static boolean shouldProjectSpectatorSteve(
            @Nullable PlayerEntity viewer,
            @Nullable PlayerEntity target
    ) {
        return viewer != null
                && viewer.isSpectator()
                && target != null
                && isActiveVendetta(target);
    }
}
