package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;

/**
 * Stable C2S contract {@code sparkwitch:black_raven_crow_use} (owner, a right-click while flying):
 * {@code crowEntityId:varint}, then vanilla's block-hit layout (block pos, side, hit offset, inside flag), like
 * {@code sparkwitch:seeker_car_use}. The hit is only the client's claim; the server re-validates distance, sight and
 * facing from the crow before replaying an empty-hand door, button, lever, trapdoor or fence-gate use (crow-world).
 * 稳定 C2S 契约 {@code sparkwitch:black_raven_crow_use}（主人飞行期间右键）：{@code crowEntityId:varint}，随后是原版方块命中布局
 * （与 {@code seeker_car_use} 相同）。命中只是客户端的声明；服务端从乌鸦位置重新校验距离、视线与朝向后，才重放空手开门、按按钮、
 * 拉拉杆、活板门或栅栏门（crow-world）。
 */
public record BlackRavenCrowUseC2SPayload(int crowEntityId, BlockHitResult hit) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("black_raven_crow_use");
    public static final Id<BlackRavenCrowUseC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlackRavenCrowUseC2SPayload> CODEC =
            PacketCodec.of(BlackRavenCrowUseC2SPayload::write, BlackRavenCrowUseC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(crowEntityId);
        buf.writeBlockHitResult(hit);
    }

    public static BlackRavenCrowUseC2SPayload read(PacketByteBuf buf) {
        return new BlackRavenCrowUseC2SPayload(buf.readVarInt(), buf.readBlockHitResult());
    }
}
