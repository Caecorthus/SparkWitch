package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Server placement transaction behind {@link RiftGateItem#use} (plan §5.1): validate (round, alive Riftwalker, not
 * inside a gate, not stunned, SparkTraits interaction lock, floor, play area, spacing, neighbours), allocate the gate
 * number, spawn and register the entity, consume one item, play the cue and record the replay item use. Never charges
 * anything on failure. Owned by P1.
 * {@link RiftGateItem#use} 背后的服务端放置事务（plan §5.1）：校验（对局、存活的隙行者、不在门内、未眩晕、SparkTraits
 * 交互封锁、地面、play area、间距、邻居），分配门编号，生成并登记实体，消耗一个物品，播放提示并记录回放。失败时不扣任何东西。
 * 归属 P1。
 */
public final class RiftGatePlacementService {
    static final String NOT_READY_MESSAGE_KEY = "message.sparkwitch.riftwalker.not_ready";

    private RiftGatePlacementService() {
    }

    /**
     * Frozen entry point; returns the item-use result for the hand ({@code SUCCESS}/{@code CONSUME} on placement,
     * {@code FAIL} otherwise). Server thread only.
     * 冻结入口；返回该手的物品使用结果（放置成功为 {@code SUCCESS}/{@code CONSUME}，否则为 {@code FAIL}）。仅服务端线程。
     */
    public static ActionResult tryPlace(ServerPlayerEntity player, Hand hand) {
        // TODO(P1): the placement transaction. G0 stub: inert refusal. / TODO(P1)：放置事务。G0 存根：无效果地拒绝。
        player.sendMessage(Text.translatable(NOT_READY_MESSAGE_KEY), true);
        return ActionResult.FAIL;
    }
}
