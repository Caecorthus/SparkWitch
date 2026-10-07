package dev.caecorthus.sparkwitch.roles.killer.magician;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record UseMagicianAbilityC2SPacket(int action) implements CustomPayload {
 public static final int ADVANCE=0, START_RECORDING=1, STOP_RECORDING=2, START_PLAYBACK=3, STOP_PLAYBACK=4;
 public static final Identifier PAYLOAD_ID=SparkWitch.id("magician_ability");
 public static final Id<UseMagicianAbilityC2SPacket> ID=new Id<>(PAYLOAD_ID);
 public static final PacketCodec<RegistryByteBuf,UseMagicianAbilityC2SPacket> CODEC=PacketCodec.of(UseMagicianAbilityC2SPacket::write,UseMagicianAbilityC2SPacket::read);
 @Override public Id<? extends CustomPayload> getId(){return ID;}
 public void write(PacketByteBuf buf){buf.writeVarInt(Math.max(ADVANCE, Math.min(STOP_PLAYBACK, action)));}
 public static UseMagicianAbilityC2SPacket read(PacketByteBuf buf){return new UseMagicianAbilityC2SPacket(buf.readVarInt());}
}
