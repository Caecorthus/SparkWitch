package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 豪气冲天's forced crawl (陆泳), read on every side from the synced {@link SphinxHaoWorldComponent} window against the
 * local world clock: the server, the crawler's own client and every observer run the same rule for the same player, so
 * pose, hitbox (0.6 high) and crawl speed agree without a packet of their own. The pose releases on the first tick the
 * window is over ({@code endTick <= now}) or the server releases the player, without waiting for a resync.
 * <p>Vanilla parity: a rider (seat, vehicle), a sleeper and a spectator keep vanilla's pose, as vanilla never puts any
 * of them in the swimming pose either; the crawler set itself only ever holds living players (server authority).
 * 豪气冲天的强制陆泳：各端都依据已同步的 {@link SphinxHaoWorldComponent} 窗口与本地世界时钟读取：服务端、陆泳者本人的客户端与所有
 * 观察者对同一玩家执行同一规则，因此姿势、碰撞箱（高 0.6）与爬行速度一致，无需额外数据包。窗口结束（{@code endTick <= now}）
 * 或服务端释放该玩家后的第一刻即恢复姿势，不必等待重新同步。
 * <p>与原版一致：骑乘者（座位、载具）、睡眠者与旁观者保持原版姿势，原版也从不让他们进入游泳姿势；陆泳者集合本身只会包含
 * 存活玩家（服务端权威）。
 */
public final class SphinxHaoCrawlPose {
    private SphinxHaoCrawlPose() {
    }

    /**
     * Whether {@code player} is forced into the swimming pose this tick. Called every tick for every player on both
     * sides: the window test comes first and is a field read when no window runs. A missing component never forces.
     * 该玩家本刻是否被强制为游泳姿势。两端每刻对每名玩家调用：先判断窗口，没有窗口时只是一次字段读取。组件缺失时从不强制。
     */
    public static boolean forcesCrawl(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        SphinxHaoWorldComponent window = SphinxHaoWorldComponent.KEY.getNullable(world);
        if (window == null || !window.isRoarActive(world.getTime())) {
            return false;
        }
        return forcesCrawl(window.isCrawling(player.getUuid(), world.getTime()), player.isSpectator(),
                player.hasVehicle(), player.isSleeping());
    }

    /** Pure decision. / 纯判定。 */
    public static boolean forcesCrawl(boolean crawling, boolean spectator, boolean riding, boolean sleeping) {
        return crawling && !spectator && !riding && !sleeping;
    }
}
