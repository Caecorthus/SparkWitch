package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoComponent;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * While Koko is live she sees every other participant through walls: an {@code always} outline, so no instinct key is
 * needed. Only her own client answers, from the owner-synced component.
 * 可可直播期间可以透视（穿墙描边）其他所有参与者：{@code always} 描边，无需按本能键。只有她自己的客户端作答。
 */
public final class KokoInstinctHooks {
    private static final int PRIORITY = 900;
    private static boolean registered;

    private KokoInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(KokoInstinctHooks::highlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || targetPlayer == viewer) {
            return null;
        }
        // Owner 2026-11-13, "like the Survival Master": an unseen living Koko is skipped (priority 100);
        // with line of sight she is an ordinary target. 所有者 2026-11-13「类似生存大师」：没有视线的存活可可被
        // skip（优先级 100）；有视线时她是普通目标。
        if (isKoko(targetPlayer)
                && GameFunctions.isPlayerPlayingAndAlive(viewer)
                && GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                && !viewer.canSee(targetPlayer)) {
            return GetInstinctHighlight.HighlightResult.skip();
        }
        var role = GameWorldComponent_GET(viewer);
        if (!KokoRules.ROLE_ID.equals(role)) {
            return null;
        }
        if (!KokoComponent.KEY.get(viewer).isStreaming()) {
            return null;
        }
        if (!isShownTo(viewer, targetPlayer)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(KokoRules.OUTLINE_COLOR, PRIORITY);
    }

    private static boolean isKoko(PlayerEntity player) {
        var role = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && KokoRules.ROLE_ID.equals(role.identifier());
    }

    private static @Nullable net.minecraft.util.Identifier GameWorldComponent_GET(ClientPlayerEntity viewer) {
        var role = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return role == null ? null : role.identifier();
    }

    private static boolean isShownTo(ClientPlayerEntity viewer, PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisible()
                && !target.isInvisibleTo(viewer)
                && !NoellesTaotieSeekerBridge.isSwallowed(target);
    }
}
