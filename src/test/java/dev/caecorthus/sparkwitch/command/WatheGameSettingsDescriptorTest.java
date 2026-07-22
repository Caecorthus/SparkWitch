package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.doctor4t.wathe.command.GameSettingsCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WatheGameSettingsDescriptorTest {
    @Test
    void wathe156RegisterDescriptorRemainsCompatible() throws ReflectiveOperationException {
        assertNotNull(GameSettingsCommand.class.getDeclaredMethod("register", CommandDispatcher.class));
    }
}
