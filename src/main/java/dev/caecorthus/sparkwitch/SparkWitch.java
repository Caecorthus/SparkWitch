package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.command.ForceAbilityCommand;
import dev.caecorthus.sparkwitch.command.SetManaCommand;
import dev.caecorthus.sparkwitch.impl.SparkWitchBuiltInSkills;
import dev.caecorthus.sparkwitch.impl.SparkWitchEvents;
import dev.caecorthus.sparkwitch.net.SparkWitchPackets;
import dev.caecorthus.sparkwitch.net.SparkWitchVersionHandshake;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SparkWitch implements ModInitializer {
    public static final String MOD_ID = "sparkwitch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SparkWitchSounds.register();
        SparkWitchItems.register();
        SparkWitchRoles.register();
        SparkWitchBuiltInSkills.register();
        SparkWitchPackets.register();
        SparkWitchVersionHandshake.registerServer();
        SparkWitchEvents.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SparkWitchRoles.refreshAssassinGuessRoleOrder());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SetManaCommand.register(dispatcher);
            ForceAbilityCommand.register(dispatcher);
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
