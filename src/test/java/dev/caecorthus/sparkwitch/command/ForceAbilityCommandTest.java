package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceAbilityCommandTest {
    @AfterEach
    void clearRegistry() {
        WitchSkillRegistry.clearForTests();
    }

    @Test
    void registersForceAbilityWithAbilityAndPlayerTargets() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();

        ForceAbilityCommand.register(dispatcher);

        CommandNode<ServerCommandSource> command = dispatcher.getRoot().getChild("sparkwitch:forceAbility");
        assertNotNull(command);
        CommandNode<ServerCommandSource> ability = command.getChild("ability");
        assertNotNull(ability);
        CommandNode<ServerCommandSource> players = ability.getChild("players");
        assertNotNull(players);
        assertNotNull(players.getCommand());
    }

    @Test
    void forceAbilitySuggestionsIncludeRegisteredSparkWitchSkills() throws ExecutionException, InterruptedException {
        WitchSkillRegistry.register(new WitchSkillDefinition(
                SparkWitch.id("mighty_force"),
                0x75EDFA,
                1,
                20,
                context -> true,
                null
        ));

        var suggestions = ForceAbilityCommand.suggestSkills(null, new SuggestionsBuilder("m", 0)).get();

        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("mighty_force")));
    }

    @Test
    void bareSparkWitchAbilityIdsNormalizeToSparkWitchNamespace() {
        assertEquals(
                SparkWitch.id("mighty_force"),
                ForceAbilityCommand.normalizeSkillId(net.minecraft.util.Identifier.of("minecraft", "mighty_force"))
        );
        assertEquals(
                SparkWitch.id("mighty_force"),
                ForceAbilityCommand.normalizeSkillId(SparkWitch.id("mighty_force"))
        );
    }
}
