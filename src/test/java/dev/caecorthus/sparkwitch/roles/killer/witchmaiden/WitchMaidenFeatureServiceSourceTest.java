package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WitchMaidenFeatureServiceSourceTest {
    private static final Path WITCH_MAIDEN_SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/witchmaiden/WitchMaidenFeatureService.java"
    );
    private static final Path TOFANA_SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/item/tofana/TofanaProtectionService.java"
    );
    private static final Path MIXIN_SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/mixin/tofana/GameFunctionsTofanaProtectionMixin.java"
    );
    private static final Path MIXINS = Path.of("src/main/resources/sparkwitch.mixins.json");

    @Test
    void keepsWitchMaidenVoodooImmunityIndependentFromUniversalTofanaProtection() throws IOException {
        String source = Files.readString(WITCH_MAIDEN_SOURCE);

        assertTrue(source.contains("KillPlayer.BEFORE.register(WitchMaidenFeatureService::beforeKill);"));
        assertTrue(source.contains("WitchMaidenRules.blocksVoodooDeath("));
        assertTrue(source.contains("return KillPlayer.KillResult.cancel();"));
        assertFalse(source.contains("KillPlayer.AFTER"));
        assertFalse(source.contains("tofanaElixir"));
    }

    @Test
    void protectsAtWathesLastPreDeathSeamAndQueuesNormalRetaliation() throws IOException {
        String service = Files.readString(TOFANA_SOURCE);
        String mixin = Files.readString(MIXIN_SOURCE);
        String config = Files.readString(MIXINS);

        assertTrue(mixin.contains("boolean force"));
        assertTrue(mixin.contains("changeGameMode(Lnet/minecraft/world/GameMode;)Z"));
        assertTrue(mixin.contains("shift = At.Shift.BEFORE"));
        assertTrue(mixin.contains("require = 1"));
        assertTrue(mixin.contains("allow = 1"));
        assertTrue(mixin.contains("TofanaProtectionService.protect(victim, killer, force)"));
        assertTrue(mixin.contains("ci.cancel();"));
        assertTrue(config.contains("tofana.GameFunctionsTofanaProtectionMixin"));

        int offhand = service.indexOf("getInventory().offHand");
        int main = service.indexOf("getInventory().main");
        assertTrue(offhand >= 0 && main > offhand);
        assertFalse(service.contains("getInventory().armor"));
        assertTrue(service.contains("stack.decrement(1)"));
        assertTrue(service.contains("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(service.contains("GameEvents.ON_FINISH_FINALIZE"));
        assertTrue(service.contains("ServerWorldEvents.UNLOAD"));
        assertTrue(service.contains("ServerLifecycleEvents.SERVER_STOPPING"));

        int retaliation = service.indexOf("GameFunctions.killPlayer(");
        assertTrue(retaliation >= 0);
        assertTrue(service.indexOf("TofanaRules.DEATH_REASON_ID", retaliation) > retaliation);
        assertFalse(service.contains("TofanaRules.DEATH_REASON_ID,\n                    true"));
    }
}
