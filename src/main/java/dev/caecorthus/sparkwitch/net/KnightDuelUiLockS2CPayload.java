package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to both duelists at duel start: closes an open inventory/skill panel. Only that close needs a packet; while
 * the duel runs the client refuses to reopen from the synced duel state, so nothing is sent when it ends.
 * 决斗开始时服务端发给双方决斗者：关闭已打开的物品栏/技能面板。只有这次关闭需要数据包；决斗期间客户端依据同步的决斗状态
 * 拒绝重新打开，因此决斗结束时无需发送。
 */
public record KnightDuelUiLockS2CPayload() implements CustomPayload {
    public static final Id<KnightDuelUiLockS2CPayload> ID = new Id<>(SparkWitch.id("knight_duel_ui_lock"));
    public static final PacketCodec<RegistryByteBuf, KnightDuelUiLockS2CPayload> CODEC =
            PacketCodec.unit(new KnightDuelUiLockS2CPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
