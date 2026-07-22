package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import io.netty.buffer.Unpooled;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurNetworkingTest {
    @Test
    void registersTheActualGlobalReceiverAndPayloadCodec() {
        SaboteurNetworking.register();

        assertTrue(ServerPlayNetworking.getGlobalReceivers()
                .contains(UseSaboteurSkillC2SPacket.PAYLOAD_ID));
        assertSame(UseSaboteurSkillC2SPacket.ID, new UseSaboteurSkillC2SPacket().getId());

        RegistryByteBuf buffer = new RegistryByteBuf(
                Unpooled.buffer(),
                DynamicRegistryManager.EMPTY
        );
        try {
            UseSaboteurSkillC2SPacket original = new UseSaboteurSkillC2SPacket();
            UseSaboteurSkillC2SPacket.CODEC.encode(buffer, original);
            assertEquals(original, UseSaboteurSkillC2SPacket.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void registeredReceiverDispatchesItsPlayerIntoTheAbilityHandler() {
        AtomicReference<String> receivedPlayer = new AtomicReference<>();

        boolean used = SaboteurNetworking.dispatch("saboteur", player -> {
            receivedPlayer.set(player);
            return true;
        });

        assertTrue(used);
        assertEquals("saboteur", receivedPlayer.get());
    }
}
