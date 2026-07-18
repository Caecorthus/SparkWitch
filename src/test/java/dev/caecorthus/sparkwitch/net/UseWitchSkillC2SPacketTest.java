package dev.caecorthus.sparkwitch.net;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UseWitchSkillC2SPacketTest {
    @Test
    void roundTripsTheExistingOptionalTargetFieldInPlace() {
        assertRoundTrip(new UseWitchSkillC2SPacket());
        assertRoundTrip(new UseWitchSkillC2SPacket(Optional.of(
                UUID.fromString("00000000-0000-0000-0000-000000000440")
        )));
    }

    private static void assertRoundTrip(UseWitchSkillC2SPacket expected) {
        PacketByteBuf buffer = PacketByteBufs.create();
        try {
            expected.write(buffer);
            assertEquals(expected, UseWitchSkillC2SPacket.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
