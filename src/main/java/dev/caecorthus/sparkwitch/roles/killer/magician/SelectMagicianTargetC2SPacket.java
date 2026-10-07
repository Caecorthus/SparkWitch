package dev.caecorthus.sparkwitch.roles.killer.magician;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;
public record SelectMagicianTargetC2SPacket(UUID target) implements CustomPayload {
 public static final Identifier PAYLOAD_ID=SparkWitch.id("magician_target");
 public static final Id<SelectMagicianTargetC2SPacket> ID=new Id<>(PAYLOAD_ID);
 public static final PacketCodec<RegistryByteBuf,SelectMagicianTargetC2SPacket> CODEC=PacketCodec.of((p,b)->b.writeUuid(p.target()),b->new SelectMagicianTargetC2SPacket(b.readUuid()));
 @Override public Id<? extends CustomPayload> getId(){return ID;}
}
