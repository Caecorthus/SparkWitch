package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Duel body collision (spec 决斗时双方无碰撞体积; owner 2026-10-30: duelists pass through every player and every player
 * passes through them). Read by {@code mixin/knight/KnightDuelEntityCollisionMixin} (Wathe's solid player wall) and
 * {@code KnightDuelLivingEntityCollisionMixin} (soft pushing) on both logical sides from the auto-synced
 * {@link KnightDuelWorldComponent}, so client movement prediction and server movement checks agree. Player pairs
 * only; block shapes, doors and every ray ({@code canHit}, knife, guns, crosshair) are untouched, so no
 * {@code RaycastShapeScope} handling is needed.
 * 决斗身体碰撞（规格“决斗时双方无碰撞体积”；所有者 2026-10-30：决斗双方穿过所有玩家，所有玩家也穿过他们）。由
 * {@code mixin/knight/KnightDuelEntityCollisionMixin}（Wathe 的玩家实体墙）与 {@code KnightDuelLivingEntityCollisionMixin}
 * （轻推挤）在双端读取自动同步的 {@link KnightDuelWorldComponent}，使客户端移动预测与服务端移动校验一致。只针对玩家对；
 * 方块形状、门与所有射线（{@code canHit}、小刀、枪械、准星）均不变，因此无需处理 {@code RaycastShapeScope}。
 */
public final class KnightDuelBodyCollision {
    private KnightDuelBodyCollision() {
    }

    /**
     * Hot path (every entity collision query): type checks, then the duel flag, then the two UUID compares. A world
     * without the component fails closed (normal collision).
     * 热路径（每次实体碰撞查询）：先判类型，再判决斗标志，最后两次 UUID 比较。没有该组件的世界按正常碰撞处理。
     */
    public static boolean ignoresPlayerBody(Entity self, Entity other) {
        if (!(self instanceof PlayerEntity selfPlayer) || !(other instanceof PlayerEntity otherPlayer)) {
            return false;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.getNullable(selfPlayer.getWorld());
        return duel != null && duel.active()
                && (duel.isDuelist(selfPlayer.getUuid()) || duel.isDuelist(otherPlayer.getUuid()));
    }
}
