package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Left-click with the 存折 passbook in the main hand: take every stored coin out. Carries nothing; the server reads the
 * main-hand stack and re-checks every gate ({@code AccountantPassbookService.handleWithdrawRequest}).
 * 主手持存折左键：取出全部存款。不携带任何数据；服务端读取主手物品并重新检查所有门槛
 * （{@code AccountantPassbookService.handleWithdrawRequest}）。
 */
public record AccountantWithdrawC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("accountant_withdraw");
    public static final Id<AccountantWithdrawC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final AccountantWithdrawC2SPacket INSTANCE = new AccountantWithdrawC2SPacket();
    public static final PacketCodec<RegistryByteBuf, AccountantWithdrawC2SPacket> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
