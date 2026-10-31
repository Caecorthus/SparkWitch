package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to both duelists: the duel UI lock. {@code locked} true closes an open inventory/skill panel; while the
 * synced duel state is active the client also refuses to open the inventory. Sending false at the end keeps the
 * client-side state symmetric (enforcement itself reads the synced duel component).
 * 服务端发给双方决斗者：决斗界面锁。{@code locked} 为 true 时关闭已打开的物品栏/技能面板；同步的决斗状态开启期间客户端
 * 也拒绝打开物品栏。结束时发送 false 保持客户端状态对称（执行本身读取同步的决斗组件）。
 */
public record KnightDuelUiLockS2CPayload(boolean locked) implements CustomPayload {
    public static final Id<KnightDuelUiLockS2CPayload> ID = new Id<>(SparkWitch.id("knight_duel_ui_lock"));
    public static final PacketCodec<RegistryByteBuf, KnightDuelUiLockS2CPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.locked),
            buf -> new KnightDuelUiLockS2CPayload(buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
