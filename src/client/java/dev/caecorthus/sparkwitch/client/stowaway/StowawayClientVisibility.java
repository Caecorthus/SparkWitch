package dev.caecorthus.sparkwitch.client.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side visibility rule for the Stowaway: while the local viewer has no line of sight ({@code canSee == false}),
 * the Stowaway must not be revealed by any instinct/outline path. The role is read from Wathe's synced component.
 * 偷渡客的客户端可见性规则：本地观察者没有视线（{@code canSee == false}）时，任何本能/描边路径都不得暴露偷渡客。
 * 职业从 Wathe 已同步的组件读取。
 */
public final class StowawayClientVisibility {
    private StowawayClientVisibility() {
    }

    public static boolean shouldHide(@Nullable PlayerEntity viewer, @Nullable Entity target) {
        if (viewer == null || viewer == target || !(target instanceof PlayerEntity targetPlayer)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(targetPlayer.getWorld());
        if (game == null || !game.isRunning() || !StowawayRules.isStowaway(game.getRole(targetPlayer))) {
            return false;
        }
        return !viewer.canSee(targetPlayer);
    }

    public static boolean shouldHideFromLocalViewer(@Nullable Entity target) {
        return shouldHide(MinecraftClient.getInstance().player, target);
    }
}
