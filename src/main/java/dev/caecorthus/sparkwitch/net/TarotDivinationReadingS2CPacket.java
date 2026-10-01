package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Carries one resolved identity or survival reading to its purchaser only; it holds exactly the bits the success
 * actionbar used to carry. {@code target} is the submitted role id or player UUID string, {@code displayName} the
 * server-resolved player name (empty for identity readings, whose name the client localizes from the role id), and
 * {@code positive} means "assigned" or "alive".
 * 仅向购买者下发一次已判定的身份或存活占卜结果，所含信息与原先成功时的动作栏消息完全相同。{@code target} 为提交的
 * 职业 id 或玩家 UUID 字符串，{@code displayName} 为服务端解析的玩家名（身份占卜为空，由客户端按职业 id 本地化），
 * {@code positive} 表示"曾分配"或"存活"。
 */
public record TarotDivinationReadingS2CPacket(
        int mode,
        String target,
        String displayName,
        boolean positive
) implements CustomPayload {
    public static final int MAX_TARGET_LENGTH = 128;
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    public static final Identifier PAYLOAD_ID = SparkWitch.id("tarot_divination_reading");
    public static final Id<TarotDivinationReadingS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, TarotDivinationReadingS2CPacket> CODEC =
            PacketCodec.of(TarotDivinationReadingS2CPacket::write, TarotDivinationReadingS2CPacket::read);

    public TarotDivinationReadingS2CPacket {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(displayName, "displayName");
        if (target.length() > MAX_TARGET_LENGTH || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Tarot reading payload is too long");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeString(target, MAX_TARGET_LENGTH);
        buf.writeString(displayName, MAX_DISPLAY_NAME_LENGTH);
        buf.writeBoolean(positive);
    }

    private static TarotDivinationReadingS2CPacket read(PacketByteBuf buf) {
        return new TarotDivinationReadingS2CPacket(
                buf.readVarInt(),
                buf.readString(MAX_TARGET_LENGTH),
                buf.readString(MAX_DISPLAY_NAME_LENGTH),
                buf.readBoolean()
        );
    }
}
