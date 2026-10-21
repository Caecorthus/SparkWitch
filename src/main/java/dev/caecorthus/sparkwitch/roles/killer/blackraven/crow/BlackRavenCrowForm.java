package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral Raven Form reads for gates on both sides (collision, name tags, Blind echoes, instinct vetoes, voice).
 * The active bit is synced to every client, so these answer the same on the server and on every client.
 * 供两端门槛（碰撞、名牌、盲人回声、本能否决、语音）使用的两端通用化鸦查询。在场标记同步给所有客户端，因此服务端与各客户端结果一致。
 */
public final class BlackRavenCrowForm {
    private BlackRavenCrowForm() {
    }

    /**
     * True while {@code player} is in Raven Form: its body stays behind invisible, frozen and without collision.
     * {@code player} 处于化鸦期间为 true：其本体原地隐身、冻结且无碰撞。
     */
    public static boolean isInCrowForm(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        return component != null && component.isActive();
    }

    /** A left-behind body (a player in Raven Form). / 被留在原地的本体（化鸦中的玩家）。 */
    public static boolean isHiddenBody(@Nullable Entity entity) {
        return entity instanceof PlayerEntity player && isInCrowForm(player);
    }

    /** The body or a crow: both are never outlined by any instinct (plan §3.2). / 本体或乌鸦：任何本能都不描边（计划 §3.2）。 */
    public static boolean isBodyOrCrow(@Nullable Entity entity) {
        return entity instanceof BlackRavenCrowEntity || isHiddenBody(entity);
    }
}
