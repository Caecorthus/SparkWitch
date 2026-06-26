package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.impl.SparkWitchBuiltInSkills;
import dev.caecorthus.sparkwitch.impl.SparkWitchEvents;
import dev.caecorthus.sparkwitch.net.SparkWitchPackets;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public final class SparkWitch implements ModInitializer {
    public static final String MOD_ID = "sparkwitch";

    @Override
    public void onInitialize() {
        SparkWitchItems.register();
        SparkWitchRoles.register();
        SparkWitchBuiltInSkills.register();
        SparkWitchPackets.register();
        SparkWitchEvents.register();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
