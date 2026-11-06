package dev.caecorthus.sparkwitch.client.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client instinct rule for the Stowaway, owner 2026-11-02 "同生存大师": exactly NoellesRoles' Survival Master rule. A
 * living viewer without line of sight ({@code canSee == false}) to a living Stowaway gets the event's {@code skip()}
 * (priority 100, the same as the Survival Master's); with line of sight the Stowaway is an ordinary target. Dead
 * viewers and spectators, vanilla Glowing and the event answers that outrank a Survival Master skip are untouched.
 * The role is the raw role from Wathe's synced component; the cheap checks run before the raycast.
 * 偷渡客的客户端本能规则，所有者 2026-11-02「同生存大师」：与 NoellesRoles 生存大师的规则完全相同。存活的观察者对存活的
 * 偷渡客没有视线（{@code canSee == false}）时，事件返回 {@code skip()}（优先级 100，与生存大师相同）；有视线时偷渡客是
 * 普通目标。死亡观察者与旁观者、原版发光效果以及能压过生存大师 skip 的事件结果均不受影响。职业读取 Wathe 已同步组件中的
 * 原始职业；廉价判断先于射线检测执行。
 */
public final class StowawayClientVisibility {
    private StowawayClientVisibility() {
    }

    public static boolean shouldHide(@Nullable PlayerEntity viewer, @Nullable Entity target) {
        if (viewer == null || viewer == target || !(target instanceof PlayerEntity targetPlayer)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(targetPlayer.getWorld());
        if (game == null || !StowawayRules.isStowaway(game.getRole(targetPlayer))) {
            return false;
        }
        return GameFunctions.isPlayerPlayingAndAlive(viewer)
                && GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                && !viewer.canSee(targetPlayer);
    }

    public static boolean shouldHideFromLocalViewer(@Nullable Entity target) {
        return shouldHide(MinecraftClient.getInstance().player, target);
    }
}
