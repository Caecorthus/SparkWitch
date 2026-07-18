package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WitchMaidenFeatureServiceSourceTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/witchmaiden/WitchMaidenFeatureService.java"
    );

    @Test
    void wiresExactVoodooCancellationAndPostDeathTofanaRetaliation() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("KillPlayer.BEFORE.register(WitchMaidenFeatureService::beforeKill);"));
        assertTrue(source.contains("KillPlayer.AFTER.register(WitchMaidenFeatureService::afterKill);"));
        assertTrue(source.contains("WitchMaidenRules.blocksVoodooDeath("));
        assertTrue(source.contains("return KillPlayer.KillResult.cancel();"));
        assertTrue(source.contains("GameFunctions.isPlayerPlayingAndAlive(killer)"));
        assertTrue(source.contains("victim.getInventory().main"));
        assertFalse(source.contains("victim.getInventory().combinedInventory"));
        assertFalse(source.contains("victim.getInventory().offHand"));

        int consume = source.indexOf("consumeTofana(victim)");
        int retaliate = source.indexOf("GameFunctions.killPlayer(", consume);
        assertTrue(consume >= 0 && retaliate > consume);
        assertTrue(source.contains("WitchMaidenRules.TOFANA_DEATH_REASON_ID"));
        assertFalse(source.contains("WitchMaidenRules.TOFANA_DEATH_REASON_ID, true"));
    }
}
