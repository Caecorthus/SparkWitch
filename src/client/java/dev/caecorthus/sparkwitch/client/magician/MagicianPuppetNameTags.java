package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Name-tag raycasts and Magician puppets (owner decision D6). A visible puppet is solid for every crosshair name label,
 * so the player behind it is never labelled through it; a puppet hit is then reported as its stand-in player
 * ({@link MagicianPuppetStandIn}), so Wathe's name tag (darkness, range, fade, psycho scramble, spectator role text,
 * cohort tip) and every label drawn on top of it (Wraith name blanking, witch and Team Jiahao cohort, Black Raven sensed
 * role) answer exactly as they would for the copied player. Only presentation: nothing here reaches the server.
 * Used by {@code MagicianPlaybackRoleNameMixin} (Wathe's player raycast, next to {@code WraithNameTagRaycastMixin}) and
 * by SparkWitch's own label raycasts, after their Wraith pass-through filter.
 * 名牌射线与魔术师皮套（所有者决定 D6）。可见的皮套对所有准星名牌都是实心的，不会透过它给身后的玩家贴名牌；命中皮套后
 * 改报为其替身玩家（{@link MagicianPuppetStandIn}），因此 Wathe 名牌（黑暗、距离、淡入淡出、疯魔乱码、旁观职业文本、
 * 同伙提示）以及叠加其上的所有标签（冤魂名字隐藏、魔女与嘉豪同伙、黑羽鸦感知身份）都与看向被复制玩家时完全一致。
 * 仅为显示，不会影响服务端。由 {@code MagicianPlaybackRoleNameMixin}（Wathe 的玩家射线，与
 * {@code WraithNameTagRaycastMixin} 并列）以及 SparkWitch 自己的标签射线在冤魂穿透过滤之后使用。
 */
public final class MagicianPuppetNameTags {
    private MagicianPuppetNameTags() {
    }

    /**
     * Widens a player name-tag predicate so a live puppet also stops the ray. Puppets are never players, so the Wraith
     * pass-through narrowing never removes them in either order. / 放宽玩家名牌判定，使存活皮套同样挡住射线；
     * 皮套不是玩家，因此无论先后顺序，冤魂穿透的收窄都不会把它去掉。
     */
    public static Predicate<Entity> stopAtPuppets(Predicate<Entity> predicate) {
        return candidate -> predicate.test(candidate) || isLivePuppet(candidate);
    }

    /** A puppet hit becomes a hit on its stand-in player; anything else is unchanged. / 皮套命中改为替身玩家命中。 */
    public static HitResult asCopiedPlayer(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof MagicianPlaybackEntity puppet) {
            PlayerEntity standIn = MagicianPuppetStandIn.of(puppet);
            if (standIn != null) {
                return new EntityHitResult(standIn, entityHit.getPos());
            }
        }
        return hit;
    }

    static boolean isLivePuppet(@Nullable Entity candidate) {
        return candidate instanceof MagicianPlaybackEntity puppet && puppet.isAlive() && !puppet.isRemoved();
    }
}
