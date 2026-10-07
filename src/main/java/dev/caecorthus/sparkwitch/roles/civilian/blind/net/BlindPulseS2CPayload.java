package dev.caecorthus.sparkwitch.roles.civilian.blind.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable S2C contract {@code sparkwitch:blind_pulse}, sent only to a living Blind: one perceived sound, voice, object,
 * own bump or cane tap. Wire order: x, y, z, radius (floats), durationTicks (short), kind (byte), emitterEntityId
 * (VarInt, -1 = none), playerEntityIds (VarInt count + VarInts, may be empty). It never names a player: entity ids only,
 * no UUID, name or role. The client presents it; the server decided who may hear what.
 * 稳定 S2C 契约 {@code sparkwitch:blind_pulse}，只发给存活的盲人：一次被感知的声音、语音、物体声、自身碰撞或盲杖敲击。
 * 线上顺序：x、y、z、radius（float）、durationTicks（short）、kind（byte）、emitterEntityId（VarInt，-1 表示无）、
 * playerEntityIds（VarInt 数量 + VarInt，可为空）。从不指名玩家：只有实体 id，没有 UUID、名字或职业。
 * 客户端只负责呈现；能听见什么由服务端决定。
 */
public record BlindPulseS2CPayload(float x, float y, float z, float radius, short durationTicks, byte kind,
                                   int emitterEntityId, int[] playerEntityIds) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("blind_pulse");
    public static final Id<BlindPulseS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlindPulseS2CPayload> CODEC =
            PacketCodec.of(BlindPulseS2CPayload::write, BlindPulseS2CPayload::read);

    /** A player's footsteps or other world sound attributed to them. / 归属于某玩家的脚步或其他世界声音。 */
    public static final byte SOUND = 0;
    /** Proximity voice. / 近距离语音。 */
    public static final byte VOICE = 1;
    /** A player-triggered object sound; lights the area only, never a figure (D3). / 玩家触发的物体声，只照亮环境（D3）。 */
    public static final byte OBJECT = 2;
    /** The Blind's own sound. / 盲人自己的声音。 */
    public static final byte SELF = 3;
    /** A cane tap: lights the area and reveals {@code playerEntityIds}. / 盲杖敲击：照亮环境并显示列出的玩家。 */
    public static final byte CANE = 4;

    public static final int NO_EMITTER = -1;
    /** Upper bound accepted by the decoder. / 解码器接受的上限。 */
    public static final int MAX_PLAYER_IDS = 256;
    private static final int[] NO_PLAYERS = new int[0];

    public BlindPulseS2CPayload {
        playerEntityIds = playerEntityIds == null ? NO_PLAYERS : playerEntityIds;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public boolean hasEmitter() {
        return emitterEntityId != NO_EMITTER;
    }

    public void write(PacketByteBuf buf) {
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeFloat(radius);
        buf.writeShort(durationTicks);
        buf.writeByte(kind);
        buf.writeVarInt(emitterEntityId);
        buf.writeVarInt(playerEntityIds.length);
        for (int id : playerEntityIds) {
            buf.writeVarInt(id);
        }
    }

    public static BlindPulseS2CPayload read(PacketByteBuf buf) {
        float x = buf.readFloat();
        float y = buf.readFloat();
        float z = buf.readFloat();
        float radius = buf.readFloat();
        short durationTicks = buf.readShort();
        byte kind = buf.readByte();
        int emitter = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PLAYER_IDS) {
            throw new DecoderException("Blind pulse player count out of range: " + count);
        }
        int[] players = count == 0 ? NO_PLAYERS : new int[count];
        for (int i = 0; i < count; i++) {
            players[i] = buf.readVarInt();
        }
        return new BlindPulseS2CPayload(x, y, z, radius, durationTicks, kind, emitter, players);
    }
}
