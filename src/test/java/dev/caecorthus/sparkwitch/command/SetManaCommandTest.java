package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SetManaCommandTest {
    @Test
    void registersSetManaWithAmountAndOptionalTargets() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();

        SetManaCommand.register(dispatcher);

        CommandNode<ServerCommandSource> command = dispatcher.getRoot().getChild("sparkwitch:setMana");
        assertNotNull(command);
        CommandNode<ServerCommandSource> amount = command.getChild("amount");
        assertNotNull(amount);
        assertNotNull(amount.getCommand());
        CommandNode<ServerCommandSource> targets = amount.getChild("targets");
        assertNotNull(targets);
        assertNotNull(targets.getCommand());
    }
}
